package me.sunyfusion.bdsp.activity;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import me.sunyfusion.bdsp.BdspRow;
import me.sunyfusion.bdsp.R;
import me.sunyfusion.bdsp.Unique;
import me.sunyfusion.bdsp.Utils;
import me.sunyfusion.bdsp.adapter.UniqueAdapter;
import me.sunyfusion.bdsp.db.BdspDB;
import me.sunyfusion.bdsp.receiver.NetUpdateReceiver;
import me.sunyfusion.bdsp.service.GpsService;
import me.sunyfusion.bdsp.state.BdspConfig;
import me.sunyfusion.bdsp.state.Global;
import me.sunyfusion.bdsp.tasks.UploadTask;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // CONSTANTS
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private BdspConfig bdspConfig;
    private BdspDB db;
    private Uri photoURI;
    static final int REQUEST_IMAGE_CAPTURE = 1;
    String mCurrentPhotoPath;


    /**
     * Runs on startup, creates the layout when the activity is created.
     * This is essentially the "main" method.
     *
     * @param savedInstanceState contains previous state (if saved) on entry
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        RecyclerView mRecyclerView;
        RecyclerView.Adapter mAdapter;
        RecyclerView.LayoutManager mLayoutManager;

        super.onCreate(savedInstanceState);

        Global.getInstance().init(this);
        bdspConfig = new BdspConfig(this);  // Stores all of the bdspConfig info from build app.txt
        try { bdspConfig.init(this.getAssets().open("buildApp.txt")); }
        catch(IOException e) {
            System.out.println("Error in configuration");
        }
        db = Global.getDb();
        ArrayList<Unique> uniques = bdspConfig.getUniques();
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        getSupportActionBar().setLogo(R.mipmap.logo);
        getSupportActionBar().setDisplayUseLogoEnabled(true);
        mRecyclerView = (RecyclerView) findViewById(R.id.uniques_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemViewCacheSize(uniques.size());

        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new UniqueAdapter(uniques);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch(id) {
            case R.id.action_mode_close_button:
                stopService(new Intent(getApplicationContext(),GpsService.class));
                BdspRow.getInstance().clear();
                BdspRow.clearId();
                finishAffinity();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onStart() {
        super.onStart();
        if(BdspRow.getId().isEmpty()) {
            showIdEntry(bdspConfig.getIdKey());
            getSupportActionBar().setSubtitle(bdspConfig.getIdKey() + " : ");
        }

        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        NetUpdateReceiver.netConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this,GpsService.class));
    }

    /**
     * Receives all intents returned by activities when returning to this activity.
     * Right now, this only processes the intent returned by the getImage() method
     * below
     *
     * @param requestCode integer that identifies the type of activity that the intent belongs to
     * @param resultCode  status code returned by the exiting activity
     * @param data        the intent that is being returned by the exiting activity
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                super.onActivityResult(requestCode, resultCode, data);
                if (resultCode == RESULT_OK) {
                    if(getView("Photo") != null) {
                        ((ImageView) getView("Photo").findViewById(R.id.photoView)).setImageURI(photoURI);
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View view) {
        /**
         * Switch based on id of the view that was clicked.
         * Using this method over having individual onClick
         * listeners for each object means that all onClick
         * actions are within one method, easier to debug
         */
        switch (view.getId()) {
            case R.id.submit:
                Utils.checkDate(this);
                Utils.getPhotoList(this);
                BdspRow.getInstance().send(getApplicationContext());
                ContentValues cv = BdspRow.getInstance().getRow();
                try {
                    db.insert(cv);
                } catch (SQLiteException e) {
                    Log.d("Database", "ERROR inserting: " + e.toString());
                }
                if (NetUpdateReceiver.netConnected) {
                    try {
                        AsyncTask<Void, Void, ArrayList<JSONArray>> doUpload = new UploadTask(BdspConfig.SUBMIT_URL);
                        doUpload.execute();
                    } catch (Exception e) {
                        Log.d("UPLOADER", "THAT DIDN'T WORK");
                    }
                }
                BdspRow.getInstance().clear();
                clearTextFields();
                break;
            default:
                System.out.println(view.getId());
                break;
        }
    }

    /*Returns the corresponding view for a given label in the form
        Use this to get the EditText box for a given labeled field
        so you can write to it.
     */
    public View getView(String label) {
        ViewGroup uniquesViewGroup = (ViewGroup) findViewById(R.id.uniques_view);
        for(int i = 0; i < uniquesViewGroup.getChildCount(); i++) {
            ViewGroup containerGroup = (ViewGroup) uniquesViewGroup.getChildAt(i);
            System.out.println(i);
            for(int j = 0; j < containerGroup.getChildCount(); j++) {
                ViewGroup uniqueTypeGroup = (ViewGroup) containerGroup.getChildAt(j);
                for(int k = 0; k < uniqueTypeGroup.getChildCount(); k++) {
                    TextView t = (TextView) uniqueTypeGroup.findViewById(R.id.uniqueName);
                    if(t != null && t.getText().toString().equals(label)) {
                        return uniqueTypeGroup;
                    }
                    t = (TextView) uniqueTypeGroup.findViewById(R.id.cameraText);
                    if(t != null && t.getText().toString().equals(label)) {
                        return uniqueTypeGroup;
                    }
                    t = (TextView) uniqueTypeGroup.findViewById(R.id.spinnerName);
                    if(t != null && t.getText().toString().equals(label)) {
                        return uniqueTypeGroup;
                    }
                }
            }
        }
        System.out.println("GETVIEW : Did not find");
        return null;
    }

    /**
     * Clears all editable fields within the uniques view object
     */
    private void clearTextFields() {
        /**
         * The viewgroup whose edittexts will be cleared
         */
        //((ImageView) findViewById(R.id.imageView)).setImageResource(R.drawable.border);
        ViewGroup uniquesViewGroup = (ViewGroup) findViewById(R.id.uniques_view);
        for(int i = 0; i < uniquesViewGroup.getChildCount(); i++) {
            ViewGroup containerGroup = (ViewGroup) uniquesViewGroup.getChildAt(i);
            for(int j = 0; j < containerGroup.getChildCount(); j++) {
                ViewGroup uniqueTypeGroup = (ViewGroup) containerGroup.getChildAt(j);
                for(int k = 0; k < uniqueTypeGroup.getChildCount(); k++) {
                    // THIS IS WHERE the methods of clearing each type of input go!
                    if (uniqueTypeGroup.getChildAt(k) instanceof EditText) {
                        ((EditText) uniqueTypeGroup.getChildAt(k)).setText("");
                    }
                    else if (uniqueTypeGroup.getChildAt(k) instanceof Spinner) {
                        ((Spinner) uniqueTypeGroup.getChildAt(k)).setSelection(0);
                    }
                    else if (uniqueTypeGroup.getChildAt(k) instanceof ImageView) {
                        ((ImageView) uniqueTypeGroup.getChildAt(k)).setImageResource(R.drawable.ic_add_a_photo_black_24dp);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NotNull String permissions[], int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    System.out.println("They said yes!");
                    startService(new Intent(MainActivity.this, GpsService.class));
                } else {
                    System.out.println("They said no!");
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    /**
     * Creates and shows the dialog for ID entry on startup of the application
     * This method is called by onCreate after the configuration file has been read
     *
     * @param id_key The name of the id field, displayed in the dialog and
     *               used as the primary key when submitting a row into the database
     */
    private void showIdEntry(final String id_key) {
        final EditText idTxt;
        idTxt = new EditText(this);
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle("Login");
        adb.setMessage("Enter " + id_key);
        adb.setView(idTxt);
        adb.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (idTxt.getText().toString().equals("")) {
                    showIdEntry(id_key);
                } else {
                    String id = idTxt.getText().toString().replace(' ', '_');
                    BdspRow.setId(id);
                    getSupportActionBar().setSubtitle(bdspConfig.getIdKey() + " : " + id );
                }
            }
        });
        adb.setCancelable(false);
        adb.show();
    }

    //Image submission methods
    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = Utils.getDateString("yyyyMMdd_HHmmss");
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        boolean t = BdspRow.getInstance().put(BdspRow.ColumnNames.get(BdspRow.ColumnType.PHOTO),
                "http://sunyfusion.me/projects/" + bdspConfig.table + "/" + image.getName()
        );
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }

    public void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                System.out.println(ex.getMessage());
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "me.sunyfusion.bdsp.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }
}
