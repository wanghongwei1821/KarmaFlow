package com.example.sizhang.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.sizhang.PrivateLedgerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val receivedAt = messages.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
        val parseResult = BankSmsParser.parseDetailed(sender, body, receivedAt)
        if (parseResult.resultCode == "not_boc") return
        val pendingResult = goAsync()
        val application = context.applicationContext as PrivateLedgerApplication

        receiverScope.launch {
            try {
                application.repository.recordSmsAttempt(sender, parseResult.resultCode)
                parseResult.balanceAfterCents?.let { balance ->
                    application.repository.updateBalanceFromSms(
                        amountCents = balance,
                        observedAt = parseResult.balanceObservedAt,
                    )
                }
                parseResult.transaction?.let { parsed ->
                    val inserted = application.repository.saveSmsTransaction(parsed)
                    if (!inserted) application.repository.recordSmsAttempt(sender, "duplicate")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
