package me.sunyfusion.fuzion;

import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.FileAsyncHttpResponseHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.auth.AuthScope;

/**
 * Created by jesse on 3/15/16.
 */
public class HTTPFunc {
    private static final String BASE_URL = "http://sunyfusion.me";
    private static AsyncHttpClient client = new AsyncHttpClient();

}
