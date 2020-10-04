package com.example.ugd6_9590_e;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.geocoding.v5.models.CarmenFeature;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete;
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

public class MainActivity extends AppCompatActivity implements Style.OnStyleLoaded,
        PermissionsListener, MapboxMap.OnMapClickListener {

    private static final String DESTINATION_SYMBOL_LAYER_ID="destination-symbol-layer-id";
    private static final String DESTINATION_ICON_ID="destination-icon-id";
    private static final String DESTINATION_SOURCE_ID = "destination-source-id";
    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    private FloatingActionButton searchfab;
    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private MapView mapView;
    private Point originPoint;
    private Point destinationPoint;
    private LocationComponent locationComponent;
    private Button navButton;
    private DirectionsRoute currentRoute;
    private static final String TAG = "DirectionsActivity";
    private NavigationMapRoute navigationMapRoute;

    private String geojsonSourceLayerId = "geojsonSourceLayerId";
    private String symbolIconId = "symbolIconId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this,getString(R.string.mapbox_access_token));
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull MapboxMap mapboxMap) {
                MainActivity.this.mapboxMap = mapboxMap;
                mapboxMap.setStyle(Style.MAPBOX_STREETS, MainActivity.this);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.style_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.street:
                mapboxMap.setStyle(Style.MAPBOX_STREETS, MainActivity.this);
                return true ;
            case R.id.satelite:
                mapboxMap.setStyle(Style.SATELLITE, MainActivity.this);
                return true ;
            case R.id.satelite_street:
                mapboxMap.setStyle(Style.SATELLITE_STREETS, MainActivity.this);
                return true ;
            case R.id.outdors:
                mapboxMap.setStyle(Style.OUTDOORS, MainActivity.this);
                return true ;
            case R.id.traffic_day:
                mapboxMap.setStyle(Style.TRAFFIC_DAY, MainActivity.this);
                return true ;
            case R.id.traffic_night:
                mapboxMap.setStyle(Style.TRAFFIC_NIGHT, MainActivity.this);
                return true ;
            case R.id.dark:
                mapboxMap.setStyle(Style.DARK, MainActivity.this);
                return true ;
            case R.id.light:
                mapboxMap.setStyle(Style.LIGHT, MainActivity.this);
                return true ;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "Grant Location Permission",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if(granted){
            mapboxMap.getStyle(new Style.OnStyleLoaded(){
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else{
            Toast.makeText(this, "Permission not granted",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onStyleLoaded(@NonNull Style style) {
        enableLocationComponent(style);
        initLayers(style);

        searchLoc(style);
        mapboxMap.addOnMapClickListener(MainActivity.this);

        navButton = findViewById(R.id.navButton);
        navButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean simulateRoute = true;
                NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                        .directionsRoute(currentRoute)
                        .shouldSimulateRoute(simulateRoute)
                        .build();

                NavigationLauncher.startNavigation(MainActivity.this, options);
            }
        });

    }


    @Override
    public boolean onMapClick(@NonNull LatLng point) {

        destinationPoint = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        originPoint = Point.fromLngLat(locationComponent.getLastKnownLocation().getLongitude(),
                locationComponent.getLastKnownLocation().getLatitude());

        GeoJsonSource source = mapboxMap.getStyle().
                getSourceAs("destination-source-id");
        if (source != null) {
            source.setGeoJson(Feature.fromGeometry(destinationPoint));
        }

        getRoute(originPoint, destinationPoint);
        navButton.setEnabled(true);
        navButton.setBackgroundResource(R.color.mapboxBlue);

        return true;
    }

    private void initSearchFab() {
        searchfab = findViewById(R.id.fab_location_search);

        searchfab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new PlaceAutocomplete.IntentBuilder()
                        .accessToken(Mapbox.getAccessToken() != null ?
                                Mapbox.getAccessToken():getString(R.string.mapbox_access_token))
                        .placeOptions(PlaceOptions.builder()
                                .backgroundColor(Color.parseColor("#EEEEEE"))
                                .limit(10)
                                .build(PlaceOptions.MODE_CARDS))
                        .build(MainActivity.this);
                startActivityForResult(intent,REQUEST_CODE_AUTOCOMPLETE);
            }
        });
    }

    private void searchLoc(@NonNull Style style){
        initSearchFab();

        style.addImage(symbolIconId, BitmapFactory.decodeResource(
                MainActivity.this.getResources(), R.drawable.mapbox_marker_icon_default));

        setUpSourceLayer(style);
    }

    private void setUpSourceLayer(@NonNull Style loadedMapStyle) {
        loadedMapStyle.addSource(new GeoJsonSource(geojsonSourceLayerId));
        loadedMapStyle.addLayer(new SymbolLayer("SYMBOL_LAYER_ID",
                geojsonSourceLayerId).withProperties(
                iconImage(symbolIconId),
                iconOffset(new Float[] {0f, -8f})
        ));
    }

    private void getRoute(Point origin, Point destination) {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call,
                                           Response<DirectionsResponse> response) {
                        Log.d(TAG, "Response code: " + response.code());
                        if (response.body() == null) {
                            Log.e(TAG, "No routes found, " +
                                    "make sure you set the right user and access token.");
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Log.e(TAG, "No routes found");
                            return;
                        }
                        currentRoute = response.body().routes().get(0);

                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView,
                                    mapboxMap, R.style.NavigationMapRoute);
                        }
                        navigationMapRoute.addRoute(currentRoute);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                        Log.e(TAG, "Error: " + throwable.getMessage());
                    }
                });
    }

    @SuppressLint("MissingPermission")
    private void enableLocationComponent(@NonNull Style loadedMapStyle){
        if(PermissionsManager.areLocationPermissionsGranted(this)){
            locationComponent = mapboxMap.getLocationComponent();

            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this,loadedMapStyle)
                            .build()
            );

            locationComponent.setLocationComponentEnabled(true);

            locationComponent.setCameraMode(CameraMode.TRACKING);

            locationComponent.setRenderMode(RenderMode.COMPASS);
            originPoint = Point.fromLngLat(locationComponent.getLastKnownLocation()
                            .getLongitude(),
                    locationComponent.getLastKnownLocation().getLatitude());
        }else{
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    private void initLayers(@NonNull Style loadedMapStyle){
        loadedMapStyle.addImage(DESTINATION_ICON_ID,
                BitmapFactory.decodeResource(this.getResources(),
                        R.drawable.mapbox_marker_icon_default));
        GeoJsonSource geoJsonSource = new GeoJsonSource(DESTINATION_SOURCE_ID);
        loadedMapStyle.addSource(geoJsonSource);
        SymbolLayer destinationSymbolLayer = new SymbolLayer(DESTINATION_SYMBOL_LAYER_ID,
                DESTINATION_SOURCE_ID);
        destinationSymbolLayer.withProperties(
                iconImage(DESTINATION_ICON_ID),
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );
        loadedMapStyle.addLayer(destinationSymbolLayer);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode== Activity.RESULT_OK && requestCode==REQUEST_CODE_AUTOCOMPLETE){
            CarmenFeature selectedCarmentFeature = PlaceAutocomplete.getPlace(data);

            if(mapboxMap!=null){
                Style style = mapboxMap.getStyle();
                if(style!=null){
                    GeoJsonSource source = style.getSourceAs("geojsonSourceLayerId");
                    if(source!=null){
                        source.setGeoJson(FeatureCollection.fromFeatures(
                                new Feature[] {Feature.fromJson(selectedCarmentFeature
                                        .toJson())}));
                    }

                    mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                                    .target(new LatLng(((Point) selectedCarmentFeature
                                            .geometry()).latitude(),
                                            ((Point) selectedCarmentFeature
                                                    .geometry()).longitude()))
                                    .zoom(17)
                                    .build()),4000);
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        super.onLowMemory();
    }

}