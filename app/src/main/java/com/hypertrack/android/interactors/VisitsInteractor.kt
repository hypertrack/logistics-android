package com.hypertrack.android.interactors

import android.content.res.Resources
import com.hypertrack.android.models.VisitPhoto
import com.hypertrack.android.models.VisitPhotoState
import com.hypertrack.android.repository.VisitsRepository
import com.hypertrack.android.toBase64
import com.hypertrack.android.utils.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

interface VisitsInteractor {
    suspend fun addPhotoToVisit(visitId: String, imagePath: String)
    fun retryVisitPhotoUpload(visitId: String, visitPhoto: VisitPhoto)
    val photoErrorFlow: MutableSharedFlow<Exception>
}

class VisitsInteractorImpl(
    private val visitsRepository: VisitsRepository,
    private val imageDecoder: ImageDecoder,
    private val photoUploadInteractor: PhotoUploadInteractor
) : VisitsInteractor {

    override val photoErrorFlow = photoUploadInteractor.errorFlow

    override suspend fun addPhotoToVisit(visitId: String, imagePath: String) = coroutineScope {
        // Log.d(TAG, "Update image for visit $id")
        val generatedImageId = UUID.randomUUID().toString()

        val target = visitsRepository.getVisit(visitId) ?: return@coroutineScope
        val previewMaxSideLength: Int = (200 * Resources.getSystem().displayMetrics.density).toInt()
        withContext(Dispatchers.Default) {
            val bitmap = imageDecoder.readBitmap(imagePath, previewMaxSideLength)
            val photo = VisitPhoto(
                imageId = generatedImageId,
                filePath = imagePath,
                base64thumbnail = bitmap.toBase64(),
                state = VisitPhotoState.NOT_UPLOADED
            )
            target.photos.add(photo)
            visitsRepository.updateItem(visitId, target)
            photoUploadInteractor.addToQueue(visitId, photo)
            // Log.v(TAG, "Updated icon in target $target")
        }
    }

    override fun retryVisitPhotoUpload(visitId: String, visitPhoto: VisitPhoto) {
        photoUploadInteractor.addToQueue(visitId, visitPhoto)
    }
}