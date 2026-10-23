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
        val actions = listOf(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_VOICE_INPUT_SETTINGS,
            Settings.ACTION_SETTINGS,
        )
        for (action in actions) {
            try {
                context.startActivity(Intent(action))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next system screen.
            }
        }
    }
}
