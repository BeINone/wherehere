package kr.dsm.wherehere;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.json.JSONArray;
import org.json.JSONException;

import cz.msebera.android.httpclient.Header;
import kr.dsm.wherehere.dto.Map;
import kr.dsm.wherehere.http.HttpConst;
import kr.dsm.wherehere.http.HttpResponseParser;

/**
 * Created by BeINone on 2017-03-31.
 */

public class MapFragment extends Fragment {

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 10;

    private AsyncHttpClient mAsyncHttpClient;
    private GoogleMap mGoogleMap;
    private MapView mMapView;
    private GoogleApiClient mGoogleApiClient;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_map, container, false);

        mAsyncHttpClient = new AsyncHttpClient();
        mGoogleApiClient = new GoogleApiClient.Builder(getContext())
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {
                        moveCameraToCurrentLocation();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                    }
                })
                .addApi(LocationServices.API)
                .build();

        mMapView = (MapView) rootView.findViewById(R.id.mapview);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mGoogleMap = googleMap;
                initializeMap();
            }
        });

        return rootView;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initializeMap();
                } else {
                    Toast.makeText(getContext(), R.string.need_location_permission, Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mMapView != null) mMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMapView != null) mMapView.onStart();
        if (mGoogleApiClient != null) mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMapView != null) mMapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMapView != null) mMapView.onStop();
        if (mGoogleApiClient != null) mGoogleApiClient.disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMapView != null) mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mMapView != null) mMapView.onLowMemory();
    }

    private void initializeMap() {
        if (mGoogleMap != null) {
            // show My Location button
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mGoogleMap.setMyLocationEnabled(true);
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
//            try {
//                mGoogleMap.setMyLocationEnabled(true);
//            } catch (SecurityException e) {
//                e.printStackTrace();
//                Toast.makeText(getContext(), R.string.need_location_permission, Toast.LENGTH_SHORT).show();
//                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                        PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
//            }

            if (mAsyncHttpClient != null) {
                mAsyncHttpClient.get(HttpConst.SERVER_URL + "/getpost.do", new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                        super.onSuccess(statusCode, headers, response);
                        try {
                            SparseArray<Map> mapSparseArray = HttpResponseParser.parseLoadPlaceJSON(response);
                            MapStorage.setMapSparseArray(mapSparseArray);
                            drawMarkers();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(getContext(), R.string.map_load_error, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        super.onFailure(statusCode, headers, responseString, throwable);
                        Toast.makeText(getContext(), R.string.map_load_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            mGoogleMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                @Override
                public void onInfoWindowClick(Marker marker) {
                    Intent intent = new Intent(getActivity(), PostActivity.class);
                    intent.putExtra(getString(R.string.extra_postnum), (int) marker.getTag());
                    getActivity().startActivity(intent);
                }
            });
        }
    }

    private void drawMarkers() {
        if (MapStorage.getMapSparseArray() != null) {
            for (int index = 0; index < MapStorage.getMapSparseArray().size(); index++) {
                Map map = MapStorage.getMapSparseArray().get(index);
                mGoogleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(map.getX(), map.getY()))
                        .title(map.getTitle())
                        .snippet(map.getWriter()))
                        .setTag(map.getPostNum());
            }
        }
    }

    private void moveCameraToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (lastLocation != null) {
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 16.0f));
            } else {
                Toast.makeText(getContext(), R.string.map_get_current_location_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
//        try {
//            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
//            if (lastLocation != null) {
//                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
//                        new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 16.0f));
//            } else {
//                Toast.makeText(getContext(), R.string.map_get_current_location_error, Toast.LENGTH_SHORT).show();
//            }
//        } catch (SecurityException e) {
//            e.printStackTrace();
//            Toast.makeText(getContext(), R.string.need_location_permission, Toast.LENGTH_SHORT).show();
//            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
//        }
    }

}
