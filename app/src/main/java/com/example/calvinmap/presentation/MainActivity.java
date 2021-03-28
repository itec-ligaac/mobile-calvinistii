package com.example.calvinmap.presentation;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.calvinmap.R;
import com.here.sdk.core.Anchor2D;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.LanguageCode;
import com.here.sdk.core.Point2D;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.gestures.GestureState;
import com.here.sdk.gestures.TapListener;
import com.here.sdk.mapviewlite.MapImage;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapMarkerImageStyle;
import com.here.sdk.mapviewlite.MapPolyline;
import com.here.sdk.mapviewlite.MapPolylineStyle;
import com.here.sdk.mapviewlite.MapScene;
import com.here.sdk.mapviewlite.MapStyle;
import com.here.sdk.mapviewlite.MapViewLite;
import com.here.sdk.mapviewlite.PickMapItemsCallback;
import com.here.sdk.mapviewlite.PickMapItemsResult;
import com.here.sdk.mapviewlite.PixelFormat;
import com.here.sdk.routing.CalculateRouteCallback;
import com.here.sdk.routing.CarOptions;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.RoutingError;
import com.here.sdk.routing.Waypoint;
import com.here.sdk.search.CategoryQuery;
import com.here.sdk.search.Place;
import com.here.sdk.search.PlaceCategory;
import com.here.sdk.search.SearchCallback;
import com.here.sdk.search.SearchEngine;
import com.here.sdk.search.SearchError;
import com.here.sdk.search.SearchOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN ACTIVITY";

    private MapViewLite mapView;
    private RadioGroup radioGroupInterests;

    private String placeCategory;

    private SearchEngine searchEngine;
    private RoutingEngine routingEngine;

    private List<Waypoint> waypoints = new ArrayList<>();
    private List<MapMarker> waypointsMarkers = new ArrayList<>();

    private List<MapMarker> ipMarkers = new ArrayList<>();

    private MapPolyline routeLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        radioGroupInterests = findViewById(R.id.rgInterests);
        RadioButton rbFood = (RadioButton) findViewById(R.id.rbFood);
        rbFood.setChecked(true);

        placeCategory = PlaceCategory.EAT_AND_DRINK;

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);

        loadMapScene();

        try {
            searchEngine = new SearchEngine();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of SearchEngine failed: " + e.error.name());
        }

        try {
            routingEngine = new RoutingEngine();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of RoutingEngine failed: " + e.error.name());
        }

        initListeners();
        setTapGestureHandler();
        setLongPressGestureHandler();

    }

    private void initListeners() {
        Button routeButton = findViewById(R.id.routeButton);
        Button resetButton = findViewById(R.id.resetButton);
        routeButton.setOnClickListener(v -> calculateRoute());
        resetButton.setOnClickListener(v -> reset());
    }

    private void loadMapScene() {
        // Load a scene from the SDK to render the map with a map style.
        mapView.getMapScene().loadScene(MapStyle.NORMAL_DAY, new MapScene.LoadSceneCallback() {
            @Override
            public void onLoadScene(@Nullable MapScene.ErrorCode errorCode) {
                if (errorCode == null) {
                    mapView.getCamera().setTarget(new GeoCoordinates(52.530932, 13.384915));
                    mapView.getCamera().setZoomLevel(14);
                } else {
                    Log.d(TAG, "onLoadScene failed: " + errorCode.toString());
                }
            }
        });
    }

    private void setTapGestureHandler() {
        mapView.getGestures().setTapListener(new TapListener() {
            @Override
            public void onTap(@NonNull Point2D touchPoint) {
                pickInterestPoint(touchPoint);
            }
        });
    }

    private void pickInterestPoint(final Point2D touchPoint) {
        float radiusInPixel = 2;
        mapView.pickMapItems(touchPoint, radiusInPixel, new PickMapItemsCallback() {
            @Override
            public void onMapItemsPicked(@Nullable PickMapItemsResult pickMapItemsResult) {
                if (pickMapItemsResult == null) {
                    return;
                }

                MapMarker topmostMapMarker = pickMapItemsResult.getTopmostMarker();

                if (topmostMapMarker == null) {
                    return;
                }

                searchForCategories(topmostMapMarker.getCoordinates());
            }
        });
    }

    private void calculateRoute() {

        radioGroupInterests.setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.rbFood:
                    placeCategory = PlaceCategory.EAT_AND_DRINK;
                    break;
                case R.id.rbSleep:
                    placeCategory = PlaceCategory.ACCOMODATION;
                    break;
                case R.id.rbShop:
                    placeCategory = PlaceCategory.SHOPPING;
            }
        });

        routingEngine.calculateRoute(
                waypoints,
                new CarOptions(),
                new CalculateRouteCallback() {
                    @Override
                    public void onRouteCalculated(@Nullable RoutingError routingError, @Nullable List<Route> routes) {
                        if (routingError == null) {
                            Route route = routes.get(0);
                            drawRoute(route);
                        } else {
                            Log.d(TAG, "eroare");
                        }
                    }
                }
        );
    }

    private void drawRoute(Route route) {
        GeoPolyline routeGeoPolyline;

        try {
            routeGeoPolyline = new GeoPolyline(route.getPolyline());
        } catch (InstantiationErrorException e) {
            return;
        }

        MapPolylineStyle mapPolylineStyle = new MapPolylineStyle();
        mapPolylineStyle.setWidthInPixels(5);
        mapPolylineStyle.setColor(0x0044FFA0, PixelFormat.RGBA_8888);
        routeLine = new MapPolyline(routeGeoPolyline, mapPolylineStyle);

        mapView.getMapScene().addMapPolyline(routeLine);
    }

    private void setLongPressGestureHandler() {
        mapView.getGestures().setLongPressListener(((gestureState, point2D) -> {
            if (gestureState == GestureState.BEGIN) {
                addMarker(R.drawable.map_marker, mapView.getCamera().viewToGeoCoordinates(point2D), waypointsMarkers);
                waypoints.add(new Waypoint(mapView.getCamera().viewToGeoCoordinates(point2D)));
            }
        }));
    }

    private void addMarker(int resourceId, GeoCoordinates geoCoordinates, List<MapMarker> markers) {
        MapImage mapImage = MapImageFactory.fromResource(this.getResources(), resourceId);

        Anchor2D anchor2D = new Anchor2D(0.5f, 1.0f);

        MapMarker mapMarker = new MapMarker(geoCoordinates);

        MapMarkerImageStyle mapMarkerImageStyle = new MapMarkerImageStyle();
        mapMarkerImageStyle.setAnchorPoint(anchor2D);

        mapMarker.addImage(mapImage, mapMarkerImageStyle);

        mapView.getMapScene().addMapMarker(mapMarker);
        markers.add(mapMarker);
    }

    private void searchForCategories(GeoCoordinates geoCoordinates) {
        List<PlaceCategory> categoryList = new ArrayList<>();
        categoryList.add(new PlaceCategory(placeCategory));
        CategoryQuery categoryQuery = new CategoryQuery(categoryList, geoCoordinates);

        int maxItems = 10;
        SearchOptions searchOptions = new SearchOptions(LanguageCode.EN_US, maxItems);

        searchEngine.search(categoryQuery, searchOptions, new SearchCallback() {
            @Override
            public void onSearchCompleted(SearchError searchError, List<Place> list) {

                String numberOfResults = "Search results: " + list.size() + ". See log for details.";

                for (Place searchResult : list) {

                    int drawable;

                    switch (placeCategory) {
                        case PlaceCategory.ACCOMODATION:
                            drawable = R.drawable.accomodation;
                            break;
                        case PlaceCategory.SHOPPING:
                            drawable = R.drawable.shopping;
                            break;
                        default:
                            drawable = R.drawable.eat_and_drink;
                    }
                    addMarker(drawable, searchResult.getGeoCoordinates(), ipMarkers);
                }
            }
        });
    }

    private void reset() {
        recreate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

}