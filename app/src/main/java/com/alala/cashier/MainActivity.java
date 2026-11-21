package com.alala.cashier;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

/**
 * alala cashier: a full-screen WebView around the cashier page that prints
 * straight to the built-in Sunmi printer (no dialogs, no third-party print app).
 */
public class MainActivity extends Activity {

    // The cashier page (GitHub Pages). Change here if the address ever changes.
    private static final String PAGE = "https://alalacafeom.github.io/cashier.html";
    private static final String HOST = "alalacafeom.github.io";

    private WebView web;
    private volatile SunmiPrinterService printer;

    private final InnerPrinterCallback printerCallback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            printer = service;
        }

        @Override
        protected void onDisconnected() {
            printer = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        bindPrinter();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // lets the new-order beep play
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "AlalaPrinter");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri u = Uri.parse(url);
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u)); // WhatsApp, phone, etc.
                } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { result.confirm(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface d) { result.cancel(); }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { result.confirm(); }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { result.cancel(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface d) { result.cancel(); }
                        })
                        .show();
                return true;
            }
        });

        web.loadUrl(PAGE);
    }

    private void bindPrinter() {
        try {
            InnerPrinterManager.getInstance().bindService(this, printerCallback);
        } catch (Exception ignored) { }
    }

    /** Methods the web page can call as window.AlalaPrinter.* */
    private class Bridge {
        @JavascriptInterface
        public boolean available() {
            return printer != null;
        }

        /** Prints a PNG (base64, no data: prefix) and feeds the paper. */
        @JavascriptInterface
        public String printPng(String base64) {
            SunmiPrinterService p = printer;
            if (p == null) {
                bindPrinter();
                return "printer service not connected";
            }
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) return "bad image";
                p.printerInit(null);
                p.printBitmap(bmp, null);
                p.lineWrap(4, null);
                return "ok";
            } catch (Exception e) {
                return "error: " + e;
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Stay inside the cashier (avoid closing it by accident).
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (printer == null) bindPrinter();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onDestroy() {
        try {
            InnerPrinterManager.getInstance().unBindService(this, printerCallback);
        } catch (Exception ignored) { }
        super.onDestroy();
    }
}
