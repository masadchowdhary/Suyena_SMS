package com.suyena.bulksms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import java.util.ArrayList

object SmsHelper {

    const val DEFAULT_MAX_PARTS_LIMIT = 10

    /**
     * Divides message into concatenated SMS parts.
     */
    fun divideMessage(context: Context, message: String): ArrayList<String> {
        if (message.isEmpty()) return ArrayList()
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.divideMessage(message) as ArrayList<String>
        } catch (e: Exception) {
            // Fallback estimation if SmsManager fails
            val partSize = if (message.any { it.code > 127 }) 67 else 153
            val parts = ArrayList<String>()
            var i = 0
            while (i < message.length) {
                parts.add(message.substring(i, minOf(i + partSize, message.length)))
                i += partSize
            }
            parts
        }
    }

    /**
     * Sends multipart SMS using sendMultipartTextMessage.
     * Guarantees standard cellular SMS transmission without converting to MMS.
     */
    fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
        maxPartsLimit: Int = DEFAULT_MAX_PARTS_LIMIT
    ): Result<Unit> {
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)

            if (parts.size > maxPartsLimit) {
                return Result.failure(
                    Exception("Message has ${parts.size} parts, exceeding the limit of $maxPartsLimit SMS parts to prevent MMS conversion.")
                )
            }

            val sentIntents = ArrayList<PendingIntent>()
            val deliveredIntents = ArrayList<PendingIntent>()

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

            for (i in parts.indices) {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    i,
                    Intent("SMS_SENT_${System.currentTimeMillis()}_$i"),
                    flags
                )
                sentIntents.add(sentIntent)
            }

            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, parts[0], sentIntents.firstOrNull(), null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
