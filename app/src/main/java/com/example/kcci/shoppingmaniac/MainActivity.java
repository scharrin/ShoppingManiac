package com.example.kcci.shoppingmaniac;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.kcci.shoppingmaniac.database.Database;
import com.example.kcci.shoppingmaniac.database.DiscountInfo;
import com.example.kcci.shoppingmaniac.database.Item;
import com.perples.recosdk.RECOBeacon;
import com.perples.recosdk.RECOBeaconManager;
import com.perples.recosdk.RECOBeaconRegion;
import com.perples.recosdk.RECOBeaconRegionState;
import com.perples.recosdk.RECOErrorCode;
import com.perples.recosdk.RECOMonitoringListener;
import com.perples.recosdk.RECOServiceConnectListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements RECOServiceConnectListener, RECOMonitoringListener {

    //region field
    public static final String RECO_UUID = "24DDF411-8CF1-440C-87CD-E368DAF9C93E";
    public static final boolean SCAN_RECO_ONLY = true;
    public static final boolean ENABLE_BACKGROUND_RANGING_TIMEOUT = true;
    public static final boolean DISCONTINUOUS_SCAN = false;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION = 10;
    public static final String EXTRA_ID = "itemId";
    private long mScanPeriod = 1 * 1000L;
    private long mSleepPeriod = 3 * 1000L;

    public static String LOG_TAG = "MainActivity";

    boolean isPageSlided = false;

    ConstraintLayout _constraintDrawer;
    private RecyclerView _beaconRecyclerView;
    private RecyclerView _recyclerView;
    private View _openDrawerButton;                            //항상 보이게 할 뷰
    private View _rootLayout;

    Animation _animGrowFromBottom;
    Animation _animSetToBottom;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    protected RECOBeaconManager mRecoManager;
    protected ArrayList<RECOBeaconRegion> mRegions;

    ArrayList<DiscountInfo> _discountInfoList;
    ArrayList<Bitmap> _images;
    ArrayList<String> _itemIdList;
    ArrayList<String> _beaconList;

    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialize();

        viewDiscountInfo();

    }

    //region Initialize
    private void initialize() {
        initLayout();
        setAnimation();
        addTest();
//        scanBeacon();
    }

    /**
     * 레이아웃 초기화
     */

    private void initLayout() {

        _beaconList = new ArrayList<>();

        _recyclerView = (RecyclerView) findViewById(R.id.recy_main_Item);
        _constraintDrawer = (ConstraintLayout) findViewById(R.id.cons_main_drawer);

        _beaconRecyclerView = (RecyclerView) findViewById(R.id.recy_main_drawer);
        _beaconRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        _beaconRecyclerView.setAdapter(new BeaconRecyclerAdapter(_beaconList, R.layout.each_beacon));

        _constraintDrawer.setVisibility(View.INVISIBLE);
        _constraintDrawer.bringToFront();

        _openDrawerButton = findViewById(R.id.btn_main_drawer);
        _openDrawerButton.bringToFront();

        _openDrawerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popDrawerView();
            }
        });

    }

    private void setAnimation() {
        SlidingPageAnimationListener animationListener = new SlidingPageAnimationListener();
        _animGrowFromBottom = AnimationUtils.loadAnimation(this, R.anim.translate_from_bottom);
        _animSetToBottom = AnimationUtils.loadAnimation(this, R.anim.translate_to_bottom);
        _animGrowFromBottom.setAnimationListener(animationListener);
        _animSetToBottom.setAnimationListener(animationListener);
    }

    private void addTest() {
        _beaconList.add(Database.MAIN);

        Button button = (Button) findViewById(R.id.changeList);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _beaconList.add(Database.MEAT);
                _beaconRecyclerView.setAdapter(new BeaconRecyclerAdapter(_beaconList, R.layout.each_beacon));
            }
        });
        final Database database = new Database();
        database.requestAllBeacon(new Database.LoadCompleteListener() {
            @Override
            public void onLoadComplete() {
                System.out.println(database.getBeaconList().get(0).getName());
            }
        });
    }
    //endregion

    private void popDrawerView() {
        if (isPageSlided) {
            Log.i(LOG_TAG, "slide down");
            _constraintDrawer.startAnimation(_animGrowFromBottom);
            _constraintDrawer.setVisibility(View.INVISIBLE);
//            ArrayList<> getSpottedBeacon();
//            if ()
//            generateCornerIcons();
        } else {
            Log.i(LOG_TAG, "slide up");
            _constraintDrawer.startAnimation(_animSetToBottom);
            _constraintDrawer.setVisibility(View.VISIBLE);
        }
    }

    private class SlidingPageAnimationListener implements Animation.AnimationListener {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            isPageSlided = !isPageSlided;
            Log.i(LOG_TAG, "animation terminated isPageSlided is : " + isPageSlided);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    }

    //region Beacon
    private void scanBeacon() {

        getAuthBT();
        mRecoManager = RECOBeaconManager.getInstance(
                getApplicationContext(),
                SCAN_RECO_ONLY,
                ENABLE_BACKGROUND_RANGING_TIMEOUT
        );
        mRegions = generateBeaconRegion();

        mRecoManager.setMonitoringListener(this);
        mRecoManager.setScanPeriod(mScanPeriod);
        mRecoManager.setSleepPeriod(mSleepPeriod);

        mRecoManager.bind(this);

    }


    private void getAuthBT() {

        //If a user device turns off bluetooth, request to turn it on.
        //사용자가 블루투스를 켜도록 요청합니다.
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.i("MainActivity", "The location permission (ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION) is not granted.");
                this.requestLocationPermission();
            } else {
                Log.i("MainActivity", "The location permission (ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION) is already granted.");
            }
        }
    }

    private void requestLocationPermission() {
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        _rootLayout = findViewById(R.id.cons_main_frame);

        Snackbar.make(_rootLayout, "location_permission_rationale", Snackbar.LENGTH_INDEFINITE)
                .setAction("ok", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                REQUEST_LOCATION
                        );
                    }
                }).show();
    }


    private ArrayList<RECOBeaconRegion> generateBeaconRegion() {
        ArrayList<RECOBeaconRegion> regions = new ArrayList<>();

        regions.add(new RECOBeaconRegion(RECO_UUID, 11, 111, "entrance"));
        regions.add(new RECOBeaconRegion(RECO_UUID, 11, 112, "grocery"));
        regions.add(new RECOBeaconRegion(RECO_UUID, 11, 113, "meat"));
        regions.add(new RECOBeaconRegion(RECO_UUID, 11, 114, "appliance"));

        return regions;
    }

    /**
     * 하단 감지된 비콘 메뉴 생성 및 보이기
     */

    //    public dddd getSpottedBeacon() {
//
//    }
//
//    private void generateConerIcons(int detectedBeaconsAmount ) {
//        if ( detectedBeaconsAmount / DRAWER_ROWS == 0 ) return;
//        LinearLayout _targetLayout = (LinearLayout) findViewById(R.id.hiddenLayout);
//        LinearLayout _rowLayout = new LinearLayout(this);
//        ImageView _btnConerIcon = new ImageView(this);
//
//        _rowLayout.setOrientation(LinearLayout.VERTICAL);
//        _btnConerIcon.
//
//        for (int i = 2;  i < DRAWER_COLUMS; i++) {
//            if (i * i > detectedBeaconsAmount) {
//                break;
//            } else {
//            _targetLayout.
//            }
//        }
//    }
    //endregion

    //region activity
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Snackbar.make(_rootLayout, "location_permission_granted", Snackbar.LENGTH_LONG)
                            .show();
                } else {
                    Snackbar.make(_rootLayout, "location_permission_not_granted", Snackbar
                            .LENGTH_LONG).show();
                }
            }
            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            //If the requestDiscountInfo to turn on bluetooth is denied, the app will be finished.
            //사용자가 블루투스 요청을 허용하지 않았을 경우, 어플리케이션은 종료됩니다.
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        this.stop(mRegions);
//        this.unbind();
//    }


//    protected void stop(ArrayList<RECOBeaconRegion> regions) {
//        for (RECOBeaconRegion region : regions) {
//            try {
//                mRecoManager.stopMonitoringForRegion(region);
//            } catch (RemoteException e) {
//                Log.i("RecoMonitoringActivity", "Remote Exception");
//                e.printStackTrace();
//            } catch (NullPointerException e) {
//                Log.i("RecoMonitoringActivity", "Null Pointer Exception");
//                e.printStackTrace();
//            }
//        }
//    }

    private void unbind() {
        try {
            mRecoManager.unbind();
        } catch (RemoteException e) {
            Log.i("RecoMonitoringActivity", "Remote Exception");
            e.printStackTrace();
        }
    }

    //endregion

    //region beacon2
    @Override
    public void didEnterRegion(RECOBeaconRegion recoBeaconRegion, Collection<RECOBeacon> collection) {
        ////////비콘 범위 진입 시 콜백
//        TextView drawerTxt = (TextView) findViewById(R.id.txtVNoSpotted);
//        drawerTxt.setText(recoBeaconRegion.getUniqueIdentifier());
    }

    @Override
    public void didExitRegion(RECOBeaconRegion recoBeaconRegion) {

    }

    @Override
    public void didStartMonitoringForRegion(RECOBeaconRegion recoBeaconRegion) {

    }

    @Override
    public void didDetermineStateForRegion(RECOBeaconRegionState recoBeaconRegionState, RECOBeaconRegion recoBeaconRegion) {

    }

    @Override
    public void monitoringDidFailForRegion(RECOBeaconRegion recoBeaconRegion, RECOErrorCode recoErrorCode) {

    }

    @Override
    public void onServiceConnect() {
        this.start(mRegions);
    }

    @Override
    public void onServiceFail(RECOErrorCode recoErrorCode) {

    }

    private void start(ArrayList<RECOBeaconRegion> mRegions) {
        for (RECOBeaconRegion region : mRegions) {
            try {
//                region.setRegionExpirationTimeMillis(60*1000L);
                mRecoManager.startMonitoringForRegion(region);
            } catch (RemoteException e) {
                Log.i("RECOMonitoringActivity", "Remote Exception");
                e.printStackTrace();
            } catch (NullPointerException e) {
                Log.i("RecoMonitoringActivity", "Null Pointer Exception");
                e.printStackTrace();
            }
        }
    }
    //endregion

    //region viewToScreen

    /**
     * Discount 정보
     */
    private void viewDiscountInfo() {
        final Database database = new Database();
        database.requestDiscountInfo(new Database.LoadCompleteListener() {
            @Override
            public void onLoadComplete() {
                _itemIdList = new ArrayList<>();
                _discountInfoList = database.getDiscountInfoList();
                for (int i = 0; i < _discountInfoList.size(); i++) {
                    _itemIdList.add(_discountInfoList.get(i).getItemId());
                }

                database.requestImageList(_itemIdList, new Database.LoadCompleteListener() {
                    @Override
                    public void onLoadComplete() {
                        _images = new ArrayList<>();
                        for (int i = 0; i < _discountInfoList.size(); i++) {
                            _images.add(database.getBitmap(i));
                        }
                        _recyclerView.setAdapter(new DiscountRecyclerAdapter(_discountInfoList, _images, R.layout.card_discount));
                        _recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
                        _recyclerView.setItemAnimator(new DefaultItemAnimator());
                    }
                });
            }
        });
    }

    private void viewItemInfo(ArrayList<Item> itemList) {

        _recyclerView.setAdapter(new ItemRecyclerAdapter(itemList, R.layout.card_item));
        _recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        _recyclerView.setItemAnimator(new DefaultItemAnimator());
    }

    //endregion

    //region RecyclerViewAdapters
    class DiscountRecyclerAdapter extends RecyclerView.Adapter<DiscountRecyclerAdapter.ViewHolder> {

        private List<DiscountInfo> _discountInfoList;
        private List<Bitmap> _imageList;
        private int _layout;

        /**
         * 생성자
         *
         * @param discountInfoList
         * @param layout
         */
        DiscountRecyclerAdapter(List<DiscountInfo> discountInfoList, List<Bitmap> imageList, int layout) {

            _discountInfoList = discountInfoList;
            _imageList = imageList;
            _layout = layout;
        }

        /**
         * 레이아웃을 만들어서 Holer에 저장
         *
         * @param viewGroup
         * @param viewType
         * @return
         */
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

            View view = LayoutInflater.from(viewGroup.getContext()).inflate(_layout, viewGroup, false);
            return new ViewHolder(view);
        }

        @Override
        public long getItemId(int position) {
            return super.getItemId(position);
        }

        /**
         * listView getView 를 대체
         * 넘겨 받은 데이터를 화면에 출력하는 역할
         *
         * @param viewHolder
         * @param position
         */
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {

            final DiscountInfo item = _discountInfoList.get(position);
            viewHolder._discountType.setText(item.getDiscountType());
            viewHolder._name.setText(item.getName());
            viewHolder._price.setText(item.getPrice());
            viewHolder._discountedPrice.setText(item.getDiscountedPrice());
            viewHolder._img.setImageBitmap(_imageList.get(position));
            viewHolder.itemView.setTag(item);

            viewHolder._btnLineChart.setOnClickListener(new OnLineChartClickListener(item.getItemId()));
        }

        @Override
        public int getItemCount() {
            return _discountInfoList.size();
        }

        /**
         * 뷰 재활용을 위한 viewHolder
         */
        class ViewHolder extends RecyclerView.ViewHolder {

            private ImageView _img;
            private TextView _discountType;
            private TextView _name;
            private TextView _price;
            private TextView _discountedPrice;
            private Button _btnLineChart;

            public ViewHolder(View itemView) {
                super(itemView);

                _img = (ImageView) itemView.findViewById(R.id.imv_discount);
                _discountType = (TextView) itemView.findViewById(R.id.txv_discount_dcType);
                _name = (TextView) itemView.findViewById(R.id.txv_discount_Name);
                _price = (TextView) itemView.findViewById(R.id.txv_item_price);
                _discountedPrice = (TextView) itemView.findViewById(R.id.txv_discount_dcPrice);
                _btnLineChart = (Button) itemView.findViewById(R.id.btn_discount_lineChart);
            }

        }
    }

    class ItemRecyclerAdapter extends RecyclerView.Adapter<ItemRecyclerAdapter.ViewHolder> {

        private List<Item> _itemList;
        private int _itemLayout;

        /**
         * 생성자
         *
         * @param items
         * @param itemLayout
         */
        ItemRecyclerAdapter(List<Item> items, int itemLayout) {

            _itemList = items;
            _itemLayout = itemLayout;
        }

        /**
         * 레이아웃을 만들어서 Holer에 저장
         *
         * @param viewGroup
         * @param viewType
         * @return
         */
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(_itemLayout, viewGroup, false);
            return new ViewHolder(view);
        }


        /**
         * listView getView 를 대체
         * 넘겨 받은 데이터를 화면에 출력하는 역할
         *
         * @param viewHolder
         * @param position
         */
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {

            Item item = _itemList.get(position);
            viewHolder._img.setImageBitmap(item.getImage());
            viewHolder._name.setText(item.getName());
            viewHolder._price.setText(item.getPrice());
            viewHolder.itemView.setTag(item);

            viewHolder._btnLineChart.setOnClickListener(new OnLineChartClickListener(item.getItemId()));
        }

        @Override
        public int getItemCount() {
            return _itemList.size();
        }

        /**
         * 뷰 재활용을 위한 viewHolder
         */
        class ViewHolder extends RecyclerView.ViewHolder {

            private ImageView _img;
            private TextView _name;
            private TextView _price;
            private Button _btnLineChart;

            public ViewHolder(View itemView) {
                super(itemView);

                _img = (ImageView) itemView.findViewById(R.id.imv_item);
                _name = (TextView) itemView.findViewById(R.id.txv_item_Name);
                _price = (TextView) itemView.findViewById(R.id.txv_item_price);
                _btnLineChart = (Button) itemView.findViewById(R.id.btn_item_lineChart);
            }

        }
    }

    class BeaconRecyclerAdapter extends RecyclerView.Adapter<BeaconRecyclerAdapter.ViewHolder> {

        private List<String> _beacons;
        private int _layout;

        /**
         * 생성자
         *
         * @param beacons
         * @param layout
         */
        BeaconRecyclerAdapter(List<String> beacons, int layout) {

            _beacons = beacons;
            _layout = layout;
        }

        /**
         * 레이아웃을 만들어서 Holer에 저장
         *
         * @param viewGroup
         * @param viewType
         * @return
         */
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(_layout, viewGroup, false);
            int paddingSize = (int) getResources().getDimension(R.dimen.beacon_view_holder_padding);
            view.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);
            return new ViewHolder(view);
        }


        /**
         * listView getView 를 대체
         * 넘겨 받은 데이터를 화면에 출력하는 역할
         *
         * @param viewHolder
         * @param position
         */
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {
            switch (_beacons.get(position)) {
                case Database.MAIN:
                    viewHolder._img.setImageResource(R.drawable.b);
                    viewHolder._img.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            viewDiscountInfo();
                        }
                    });
                    break;
                case Database.MEAT:
                    viewHolder._img.setImageResource(R.drawable.c);
                    viewHolder._img.setOnClickListener(new OnCategoryClickListener(Database.MEAT));
                    break;
                case Database.VEGETABLE:
                    viewHolder._img.setImageResource(R.drawable.d);
                    viewHolder._img.setOnClickListener(new OnCategoryClickListener(Database.VEGETABLE));
            }
            viewHolder._textView.setText(_beacons.get(position));
        }

        @Override
        public int getItemCount() {
            return _beacons.size();
        }

        /**
         * 뷰 재활용을 위한 viewHolder
         */
        class ViewHolder extends RecyclerView.ViewHolder {

            private ImageView _img;
            private TextView _textView;

            public ViewHolder(View itemView) {
                super(itemView);
                _img = (ImageView) itemView.findViewById(R.id.imv_beacon_image);
                _textView = (TextView) itemView.findViewById(R.id.txv_beacon_text);
            }
        }

        class OnCategoryClickListener implements View.OnClickListener {

            private String _category;

            public OnCategoryClickListener(String category) {

                _category = category;
            }

            @Override
            public void onClick(View v) {
                final Database database = new Database();
                database.requestItemByCategory(_category, new Database.LoadCompleteListener() {
                    @Override
                    public void onLoadComplete() {
                        database.getItemList();
                        viewItemInfo(database.getItemList());
                    }
                });
            }
        }
    }

    class OnLineChartClickListener implements View.OnClickListener {

        String _itemId;

        public OnLineChartClickListener(String itemId) {

            _itemId = itemId;
        }

        @Override
        public void onClick(View v) {

            Log.i(LOG_TAG, "Line Chart Start...");

            Intent intent = new Intent(getApplicationContext(), LineChartActivity.class);
            intent.putExtra(EXTRA_ID, _itemId);
            startActivity(intent);

        }
    }
    //endregion
}

