package com.example.image_texteditor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextDetector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button capture;
    private EditText filename;
    private String fname;
    private int flg = 0;
    private String imagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 120);
        }
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 121);
        }

        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 18881);
        }

        capture = (Button) findViewById(R.id.capture);
        filename = (EditText) findViewById(R.id.filename);


        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(filename.getText().toString().trim().length() > 0) {
                    fname = filename.getText().toString();
                    fname = fname.replaceAll("\\s", "_");
                    takePicture();
                }
                else
                    Toast.makeText(getApplicationContext(), "Enter a file name before taking photo ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void takePicture () {
        Intent captureImg = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureImg.resolveActivity(getPackageManager()) != null){
            File photo = null;
            try{
                photo = createImageFile();
            }catch (IOException ex){}

            if(photo != null){
                Uri photoUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photo);
                captureImg.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(captureImg, 1024);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        imagePath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1024 && resultCode == RESULT_OK) {
            Bitmap imageBitmap = orientatioCheck();
            detectTextFromImage(imageBitmap);
        }
    }

    private Bitmap orientatioCheck(){
        Bitmap bmp = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        DisplayMetrics displaymetrics;
        displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        int  screenWidth = displaymetrics.widthPixels;
        int screenHeight = displaymetrics.heightPixels;


        Matrix matrix = new Matrix();
        ExifInterface exifReader = null;
        try {
            exifReader = new ExifInterface(imagePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = exifReader.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
        int rotate = 0;
        if (orientation ==ExifInterface.ORIENTATION_NORMAL) {
            // Do nothing. The original image is fine.
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            rotate = 90;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            rotate = 180;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            rotate = 270;
        }
        matrix.postRotate(rotate);
        try {
            bmp = loadBitmap(imagePath, rotate, screenWidth, screenHeight);
        } catch (OutOfMemoryError e) {
        }
        return bmp;
    }

    public static Bitmap loadBitmap(String path, int orientation, final int targetWidth, final int targetHeight) {
        Bitmap bitmap = null;
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int sourceWidth, sourceHeight;
            if (orientation == 90 || orientation == 270) {
                sourceWidth = options.outHeight;
                sourceHeight = options.outWidth;
            } else {
                sourceWidth = options.outWidth;
                sourceHeight = options.outHeight;
            }
            if (sourceWidth > targetWidth || sourceHeight > targetHeight) {
                float widthRatio = (float)sourceWidth / (float)targetWidth;
                float heightRatio = (float)sourceHeight / (float)targetHeight;
                float maxRatio = Math.max(widthRatio, heightRatio);
                options.inJustDecodeBounds = false;
                options.inSampleSize = (int)maxRatio;
                bitmap = BitmapFactory.decodeFile(path, options);
            } else {
                bitmap = BitmapFactory.decodeFile(path);
            }
            if (orientation > 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(orientation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
            sourceWidth = bitmap.getWidth();
            sourceHeight = bitmap.getHeight();
            if (sourceWidth != targetWidth || sourceHeight != targetHeight) {
                float widthRatio = (float)sourceWidth / (float)targetWidth;
                float heightRatio = (float)sourceHeight / (float)targetHeight;
                float maxRatio = Math.max(widthRatio, heightRatio);
                sourceWidth = (int)((float)sourceWidth / maxRatio);
                sourceHeight = (int)((float)sourceHeight / maxRatio);
                bitmap = Bitmap.createScaledBitmap(bitmap, sourceWidth, sourceHeight, true);
            }
        } catch (Exception e) {
        }
        return bitmap;
    }

    private void detectTextFromImage (Bitmap bitmap){
        Log.d("Tag", "detecting text");
        File file = new File(imagePath);
        if(file.exists()){
            file.delete();
        }
        final FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionTextDetector firebaseVisionTextDetector = FirebaseVision.getInstance().getVisionTextDetector();
        firebaseVisionTextDetector.detectInImage(firebaseVisionImage).addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
            @Override
            public void onSuccess(FirebaseVisionText firebaseVisionText) {
                displayText(firebaseVisionText);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, "Error" + e.getMessage(), Toast.LENGTH_LONG);
            }
        });
    }

    private void displayText (FirebaseVisionText firebaseVisionText){
        String text = "";
        List<FirebaseVisionText.Block> blockList = firebaseVisionText.getBlocks();
        Toast.makeText(MainActivity.this, "Text found", Toast.LENGTH_LONG);
        if (blockList.size() == 0) {
            Toast.makeText(MainActivity.this, "No text found", Toast.LENGTH_LONG);
        } else {
            for (FirebaseVisionText.Block block : firebaseVisionText.getBlocks()) {
                text += block.getText();
            }
            try {
                processText(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processText (String text) throws IOException {
        if (!text.isEmpty()) {
            File txt = null;
            try {
                txt = createFile();
            } catch (IOException ex) {}
            if(txt != null){
                FileWriter writer = new FileWriter(txt);
                writer.append(text);
                writer.flush();
                writer.close();
                Toast.makeText(MainActivity.this, "Saved your text", Toast.LENGTH_LONG).show();
            }
        }
    }

    private File createFile() throws IOException {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File txt = File.createTempFile(
                    fname,
                    ".doc",
                    storageDir);
        return txt;
    }
}