package com.example.sizhang.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.sizhang.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class SmsSyncResult(
    val scanned: Int,
    val recognized: Int,
    val added: Int,
    val statusCode: String,
)

class SmsInboxSynchronizer(
    private val context: Context,
    private val repository: LedgerRepository,
) {
    suspend fun syncRecent(): SmsSyncResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext SmsSyncResult(0, 0, 0, "sync_permission_missing")
        }

        var scanned = 0
        var recognized = 0
        var added = 0
        var balanceFound = false
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(45)
        val serviceNumbers = listOf("95588", "95533", "95599", "95566", "95555", "95559", "95580")
        val bankSignatures = listOf(
            "工商银行", "建设银行", "农业银行", "中国银行", "招商银行", "交通银行",
            "邮储银行", "邮政储蓄银行",
        )
        val senderClauses = serviceNumbers.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} LIKE ?" }
        val bodyClauses = bankSignatures.joinToString(" OR ") { "${Telephony.Sms.BODY} LIKE ?" }
        val selection = "${Telephony.Sms.DATE} >= ? AND ($senderClauses OR $bodyClauses)"
        val selectionArgs = buildList {
            add(since.toString())
            serviceNumbers.forEach { add("%$it%") }
            bankSignatures.forEach { add("%$it%") }
        }.toTypedArray()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && scanned < 500) {
                    scanned++
                    val sender = cursor.getString(addressIndex).orEmpty()
                    val body = cursor.getString(bodyIndex).orEmpty()
                    val receivedAt = cursor.getLong(dateIndex)
                    val parseResult = BankSmsParser.parseDetailed(sender, body, receivedAt)
                    parseResult.balanceAfterCents?.let { balance ->
                        balanceFound = true
                        repository.updateBalanceFromSms(
                            amountCents = balance,
                            observedAt = parseResult.balanceObservedAt,
                            bank = parseResult.bank,
                            cardLast4 = parseResult.cardLast4,
                        )
                    }
                    val parsed = parseResult.transaction ?: continue
                    recognized++
                    if (repository.saveSmsTransaction(parsed)) added++
                }
            }
        } catch (_: SecurityException) {
            return@withContext SmsSyncResult(scanned, recognized, added, "sync_permission_missing")
        } catch (_: RuntimeException) {
            return@withContext SmsSyncResult(scanned, recognized, added, "sync_error")
        }

        val status = when {
            added > 0 -> "sync_added:$added"
            balanceFound -> "sync_balance"
            recognized > 0 -> "sync_no_new"
            scanned > 0 -> "sync_unrecognized"
            else -> "sync_none"
        }
        repository.recordSmsAttempt("银行同步", status)
        SmsSyncResult(scanned, recognized, added, status)
    }
}
