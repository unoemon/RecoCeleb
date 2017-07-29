package com.unoemon.recoceleb;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisInDomainResult;
import com.tbruyelle.rxpermissions2.RxPermissions;
import com.unoemon.recocereb.R;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.unoemon.recoceleb.ApiConst.API_MODEL;
import static com.unoemon.recoceleb.ApiConst.API_ROOT;
import static com.unoemon.recoceleb.ApiConst.API_SUBSCRIPT_KEY;


public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private Bitmap bitmap;

    @BindView(R.id.textview_result)
    TextView textView;

    @BindView(R.id.imageview_result)
    ImageView imageView;

    @OnClick(R.id.button_pick)
    void button_pick() {
        Log.d("MaiActivity", "button_pick");

        RxPermissions rxPermissions = new RxPermissions(this);
        rxPermissions
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    if (granted) { // Always true pre-M
                        // I can control the camera now
                        Log.d("MaiActivity", "button_pick:granted");
                        Intent intent = new Intent();
                        intent.setType("image/*");
                        intent.setAction(Intent.ACTION_GET_CONTENT);
                        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
                    } else {
                        // Camera permission denied
                        Log.d("MaiActivity", "button_pick:else");
                    }
                });
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("MaiActivity", "onCreate");
        ButterKnife.bind(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                //Display an error
                return;
            }
            bitmap = resizeBitmap(data);
            getDomain(bitmap);
        }
    }

    private Bitmap resizeBitmap(Intent data) {
        InputStream inputStream = null;
        Bitmap bitmap = null;

        try {
            inputStream = getContentResolver().openInputStream(data.getData());

            BitmapFactory.Options imageOptions = new BitmapFactory.Options();
            imageOptions.inJustDecodeBounds = true;
            imageOptions.inMutable = true;
            BitmapFactory.decodeStream(inputStream, null, imageOptions);
            Log.v("image", "Original Image Size: " + imageOptions.outWidth + " x " + imageOptions.outHeight);

            inputStream.close();

            int imageSizeMax = 500;
            inputStream = getContentResolver().openInputStream(data.getData());
            float imageScaleWidth = (float) imageOptions.outWidth / imageSizeMax;
            float imageScaleHeight = (float) imageOptions.outHeight / imageSizeMax;

            if (imageScaleWidth > 2 && imageScaleHeight > 2) {
                BitmapFactory.Options imageOptions2 = new BitmapFactory.Options();

                int imageScale = (int) Math.floor((imageScaleWidth > imageScaleHeight ? imageScaleHeight : imageScaleWidth));

                for (int i = 2; i <= imageScale; i *= 2) {
                    imageOptions2.inSampleSize = i;
                }

                bitmap = BitmapFactory.decodeStream(inputStream, null, imageOptions2);
                Log.v("image", "Sample Size: 1/" + imageOptions2.inSampleSize);
            } else {
                bitmap = BitmapFactory.decodeStream(inputStream);
            }

            inputStream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  bitmap;
    }


    private void drawFace(String name, float left, float top, float width, float height) {
        Bitmap bitmap = this.bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLUE);
        paint.setAlpha(0x77);
        paint.setTextSize(20);
        paint.setStrokeWidth(4);
        canvas.drawRect(left, top, left + width, top + height, paint);
        canvas.drawText(name, left, top, paint);

        Paint paint2 = new Paint();
        paint2.setAntiAlias(true);
        paint2.setStrokeWidth(0);
        paint2.setColor(Color.WHITE);
        paint2.setTextSize(20);
        paint2.setStyle(Paint.Style.FILL);
        canvas.drawText(name, left, top, paint2);
        this.bitmap = bitmap;
    }


    private void getDomain(Bitmap bitmap) {
            Disposable disposable = getDomainObservable(bitmap)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(strResult -> {
                        Log.d("MaiActivity", "strResult:" + strResult);
                        setViews(strResult);
                    }, e -> {
                        Log.d("MaiActivity", "error:" + e.toString());
                       Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
                    });
            DisposableManager.add(disposable);

    }

    private Observable<String> getDomainObservable(Bitmap bitmap) {
        return Observable.create(subscriber -> {

            org.apache.commons.io.output.ByteArrayOutputStream outputStream = new org.apache.commons.io.output.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

            VisionServiceClient visionServiceClient = new VisionServiceRestClient(API_SUBSCRIPT_KEY, API_ROOT);
            AnalysisInDomainResult analysisInDomainResult = visionServiceClient.analyzeImageInDomain(inputStream, API_MODEL);
            String strResult = new Gson().toJson(analysisInDomainResult);
            visionServiceClient = null;
            subscriber.onNext(strResult);
            subscriber.onComplete();
        });
    }


    private void setViews(String strResult) {
        textView.setText("");
        Gson gson = new Gson();
        StringBuffer list = new StringBuffer();
        AnalysisInDomainResult result = gson.fromJson(strResult, AnalysisInDomainResult.class);
        JsonArray detectedCelebs = result.result.get(API_MODEL).getAsJsonArray();
        Log.d("MaiActivity", "detectedCelebs:" + detectedCelebs.toString());

        for (JsonElement element : detectedCelebs) {
            JsonObject celeb = element.getAsJsonObject();
            String name = celeb.get("name").getAsString();
            double confidence = +celeb.get("confidence").getAsDouble() * 100;
            String confidenceStr = String.format("%.2f", confidence);
            list.append(name + " (" + confidenceStr + "%)\n");
            JsonObject faceRectangle = celeb.get("faceRectangle").getAsJsonObject();
            Log.d("MaiActivity", "faceRectangle:" + faceRectangle.toString());
            float left = faceRectangle.get("left").getAsFloat();
            float top = faceRectangle.get("top").getAsFloat();
            float width = faceRectangle.get("width").getAsFloat();
            float height = faceRectangle.get("height").getAsFloat();
            Log.d("MaiActivity", "left:" + left);
            drawFace(name, left, top, width, height);
        }

        textView.setText(list);
        imageView.setImageBitmap(bitmap);
    }


}