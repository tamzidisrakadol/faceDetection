package com.example.facedetector;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.facedetector.Adapter.FaceDetectionAdapter;
import com.example.facedetector.model.FaceDetectionModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.yalantis.ucrop.UCrop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements FrameProcessor {
    private Facing camerFacing = Facing.FRONT;
    private ImageView imgView, imgView2;
    private CameraView faceDetectionCameraView;
    private RecyclerView bottomSheetRecyclerView;
    private BottomSheetBehavior bottomSheetBehavior;
    private ArrayList<FaceDetectionModel> faceDetectionModels;
    private Button toggleBtn;
    private FrameLayout bottomSheetBtn;
    private ActivityResultLauncher<String> mGetContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        faceDetectionModels = new ArrayList<>();
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        imgView = findViewById(R.id.face_detection_image_view);
        imgView2 = findViewById(R.id.face_detection_camera_image_view);
        faceDetectionCameraView = findViewById(R.id.face_detection_camera_view);
        toggleBtn = findViewById(R.id.face_detection_camera_toggle_button);
        bottomSheetBtn = findViewById(R.id.bottom_sheet_button);
        bottomSheetRecyclerView = findViewById(R.id.bottom_sheet_recycler_view);

        faceDetectionCameraView.setFacing(camerFacing);
        faceDetectionCameraView.setLifecycleOwner(MainActivity.this);
        faceDetectionCameraView.addFrameProcessor(MainActivity.this);

        bottomSheetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGetContent.launch("image/*");
            }
        });
        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
            @Override
            public void onActivityResult(Uri result) {
                Intent intent = new Intent(MainActivity.this, CropperActivity.class);
                intent.putExtra("DATA", result.toString());
                startActivityForResult(intent, 101);
            }
        });

        bottomSheetRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        bottomSheetRecyclerView.setAdapter(new FaceDetectionAdapter(faceDetectionModels, MainActivity.this));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == -1 && requestCode == 101) {
            String result = data.getStringExtra("RESULT");
            Uri resultUri = Uri.parse(result);
            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            findViewById(R.id.face_detection_camera_container).setVisibility(View.GONE);
            analyseImage(resultUri, bitmap);
        }
    }

    private void analyseImage(Uri uri, Bitmap bitmap) {
        imgView.setImageURI(uri);
        faceDetectionModels.clear();
        Objects.requireNonNull(bottomSheetRecyclerView.getAdapter()).notifyDataSetChanged();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionFaceDetectorOptions faceDetectorOptions = new FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .build();
    }

    @Override
    public void process(@NonNull Frame frame) {

    }
}