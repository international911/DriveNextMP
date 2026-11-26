package com.example.drivenext

import android.app.AlertDialog
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Открывает календарь, галерею и камеру; результаты сохраняет в черновике регистрации.
class AuthMedia(
    private val activity: AuthActivity,
    private val model: AuthViewModel,
    private val gallery: ActivityResultLauncher<PickVisualMediaRequest>,
    private val camera: ActivityResultLauncher<Uri>,
    private val saveFields: () -> Unit,
    private val redraw: () -> Unit,
) {
    fun chooseDate(birth: Boolean) {
        saveFields()
        val saved = if (birth) model.draft.birthDate else model.draft.licenseIssueDate
        val formatter = DateTimeFormatter.ofPattern(if (birth) "MM/dd/uuuu" else "dd/MM/uuuu", Locale.US)
        val initial = runCatching { LocalDate.parse(saved, formatter) }.getOrNull()
            ?: if (birth) LocalDate.now().minusYears(20) else LocalDate.now()
        val constraints = CalendarConstraints.Builder()
            .setStart(LocalDate.of(1900, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointBackward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (birth) R.string.birth_date else R.string.license_issue_date)
            .setSelection(initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .setCalendarConstraints(constraints)
            .build()
        attachDatePickerListener(picker, birth)
        picker.show(activity.supportFragmentManager, if (birth) "birthDatePicker" else "issueDatePicker")
    }

    fun attachDatePickerListener(picker: MaterialDatePicker<*>, birth: Boolean) {
        picker.addOnPositiveButtonClickListener { selectedDateUtcMillis ->
            val date = Instant.ofEpochMilli(selectedDateUtcMillis as? Long ?: return@addOnPositiveButtonClickListener)
                .atZone(ZoneOffset.UTC).toLocalDate()
            val formatter = DateTimeFormatter.ofPattern(if (birth) "MM/dd/uuuu" else "dd/MM/uuuu", Locale.US)
            if (birth) model.draft.birthDate = date.format(formatter) else model.draft.licenseIssueDate = date.format(formatter)
            redraw()
        }
    }

    fun choosePhoto(kind: PhotoKind) {
        saveFields()
        model.pendingPhoto = kind
        AlertDialog.Builder(activity).setItems(arrayOf(activity.getString(R.string.choose_gallery), activity.getString(R.string.take_photo))) { _, which ->
            if (which == 0) gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else {
                val file = File.createTempFile("photo_", ".jpg", File(activity.cacheDir, "camera").apply { mkdirs() })
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                model.cameraUri = uri
                camera.launch(uri)
            }
        }.show()
    }

    fun savePhoto(uri: Uri) {
        when (model.pendingPhoto) {
            PhotoKind.AVATAR -> model.draft.avatarUri = uri
            PhotoKind.LICENSE -> model.draft.licensePhotoUri = uri
            PhotoKind.PASSPORT -> model.draft.passportPhotoUri = uri
            null -> Unit
        }
        model.pendingPhoto = null
    }
}
