package dev.scdeal.helldye.role

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

object AssistantRole {
    private fun manager(context: Context): RoleManager =
        context.getSystemService(RoleManager::class.java)

    fun isAvailable(context: Context): Boolean =
        manager(context).isRoleAvailable(RoleManager.ROLE_ASSISTANT)

    fun isHeld(context: Context): Boolean =
        manager(context).isRoleHeld(RoleManager.ROLE_ASSISTANT)

    fun requestIntent(context: Context): Intent? =
        if (isAvailable(context) && !isHeld(context)) {
            manager(context).createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
        } else {
            null
        }

    fun openSystemSettings(context: Context) {
        val intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
