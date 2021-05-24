package com.hypertrack.android.ui.screens.visits_management.tabs.places

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.hypertrack.android.api.ApiClient
import com.hypertrack.android.api.Geofence
import com.hypertrack.android.api.GeofenceMarker
import com.hypertrack.android.repository.PlacesRepository
import com.hypertrack.android.ui.base.BaseViewModel
import com.hypertrack.android.ui.base.Consumable
import com.hypertrack.android.ui.base.SingleLiveEvent
import com.hypertrack.android.ui.base.toConsumable
import com.hypertrack.android.ui.screens.visits_management.VisitsManagementFragmentDirections
import com.hypertrack.android.utils.OsUtilsProvider
import kotlinx.coroutines.launch

class PlacesViewModel(
    private val placesRepository: PlacesRepository,
    private val osUtilsProvider: OsUtilsProvider
) : BaseViewModel() {

    private var nextPageToken: String? = null

    val loadingState = MutableLiveData<Boolean>()

    val placesPage = SingleLiveEvent<Consumable<List<PlaceItem>>?>()

    fun refresh() {
        placesPage.value = null
        placesPage.postValue(null)
        nextPageToken = null
        onLoadMore()
    }

    fun createPlacesAdapter(): PlacesAdapter {
        return PlacesAdapter(osUtilsProvider)
    }

    fun onPlaceClick(placeItem: PlaceItem) {
        destination.postValue(
            VisitsManagementFragmentDirections.actionVisitManagementFragmentToPlaceDetailsFragment(
                placeItem.geofence._id
            )
        )
    }

    fun onAddPlaceClicked() {
        destination.postValue(VisitsManagementFragmentDirections.actionVisitManagementFragmentToAddPlaceFragment())
    }

    //todo test
    fun onLoadMore() {
        viewModelScope.launch {
            try {
                if (nextPageToken != null || placesPage.value == null) {
                    Log.v("cutag", "loading $nextPageToken")
                    loadingState.postValue(true)
                    val res = placesRepository.loadPage(nextPageToken)
                    nextPageToken = res.paginationToken
                    placesPage.postValue(Consumable(res.geofences.map { PlaceItem(it) }))
                    loadingState.postValue(false)
                }
            } catch (e: Exception) {
                errorBase.postValue(osUtilsProvider.getErrorMessage(e).toConsumable())
                loadingState.postValue(false)
            }
        }
    }

}