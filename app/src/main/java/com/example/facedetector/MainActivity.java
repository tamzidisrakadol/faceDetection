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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;
import com.yalantis.ucrop.UCrop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements FrameProcessor {
    private Facing camerFacing = Facing.FRONT;
    private ImageView imgView;
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
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HALF_EXPANDED);
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);


        FirebaseVisionFaceDetectorOptions faceDetectorOptions = new FirebaseVisionFaceDetectorOptions.Builder()
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                .build();

        FirebaseVisionFaceDetector firebaseVisionFaceDetector = FirebaseVision.getInstance().getVisionFaceDetector(faceDetectorOptions);

        firebaseVisionFaceDetector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(@NonNull List<FirebaseVisionFace> firebaseVisionFaces) {
                        Bitmap mutableImg = bitmap.copy(Bitmap.Config.ARGB_8888,true);
                        detectFaces(firebaseVisionFaces,mutableImg);

                        imgView.setImageBitmap(mutableImg);

                        bottomSheetRecyclerView.getAdapter().notifyDataSetChanged();
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                    }
                });

    }

    private void detectFaces(List<FirebaseVisionFace> firebaseVisionFaces, Bitmap mutableImg) {
        if(firebaseVisionFaces==null || mutableImg==null){
            Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
            return;
        }

        Canvas canvas = new Canvas(mutableImg);
        Paint facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5f);

        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.BLUE);
        faceTextPaint.setTextSize(30f);
        faceTextPaint.setTypeface(Typeface.SANS_SERIF);

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8f);

        for(int i=0; i<firebaseVisionFaces.size();i++){
            canvas.drawRect(firebaseVisionFaces.get(i).getBoundingBox(),facePaint);
            canvas.drawText("Face " + i, (firebaseVisionFaces.get(i).getBoundingBox().centerX()
                            - (firebaseVisionFaces.get(i).getBoundingBox().width() >> 2) + 8f), // added >> to avoid errors when dividing with "/"
                    (firebaseVisionFaces.get(i).getBoundingBox().centerY()
                            + firebaseVisionFaces.get(i).getBoundingBox().height() >> 2) - 8F,
                    facePaint);


            FirebaseVisionFace face = firebaseVisionFaces.get(i);

            //left eye
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE) != null) {
                FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                canvas.drawCircle(Objects.requireNonNull(leftEye).getPosition().getX(),
                        leftEye.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            //right eye
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE) != null) {
                FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
                canvas.drawCircle(Objects.requireNonNull(rightEye).getPosition().getX(),
                        rightEye.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            //nose
            if (face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE) != null) {
                FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);
                canvas.drawCircle(Objects.requireNonNull(nose).getPosition().getX(),
                        nose.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }
            //left ear
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR) != null) {
                FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
                canvas.drawCircle(Objects.requireNonNull(leftEar).getPosition().getX(),
                        leftEar.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            //right ear
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR) != null) {
                FirebaseVisionFaceLandmark rightEar = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR);
                canvas.drawCircle(Objects.requireNonNull(rightEar).getPosition().getX(),
                        rightEar.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            //mouth
            if (face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT) != null) {
                FirebaseVisionFaceLandmark leftMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT);
                FirebaseVisionFaceLandmark bottomMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM);
                FirebaseVisionFaceLandmark rightMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT);
                canvas.drawLine(leftMouth.getPosition().getX(),
                        leftMouth.getPosition().getY(),
                        bottomMouth.getPosition().getX(),
                        bottomMouth.getPosition().getY(),
                        landmarkPaint);
                canvas.drawLine(bottomMouth.getPosition().getX(),
                        bottomMouth.getPosition().getY(),
                        rightMouth.getPosition().getX(),
                        rightMouth.getPosition().getY(), landmarkPaint);
            }

            faceDetectionModels.add(new FaceDetectionModel(i, "Smiling Probability " + face.getSmilingProbability()));
            faceDetectionModels.add(new FaceDetectionModel(i, "Left Eye Open Probability " + face.getLeftEyeOpenProbability()));
            faceDetectionModels.add(new FaceDetectionModel(i, "Right Eye Open Probability " + face.getRightEyeOpenProbability()));
        }

    }

    @Override
    public void process(@NonNull Frame frame) {
        int width = frame.getSize().getWidth();
        int height = frame.getSize().getHeight();

        FirebaseVisionImageMetadata imageMetadata = new FirebaseVisionImageMetadata
                .Builder()
                .setWidth(width)
                .setHeight(height)
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setRotation((camerFacing==Facing.FRONT)?FirebaseVisionImageMetadata.ROTATION_270:FirebaseVisionImageMetadata.ROTATION_90)
                .build();

        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromByteArray(frame.getData(),imageMetadata);
        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions
                .Builder()
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        faceDetector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                        imgView.setImageBitmap(null);

                        Bitmap bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        Paint dotPaint = new Paint();
                        dotPaint.setColor(Color.RED);
                        dotPaint.setStyle(Paint.Style.FILL);
                        dotPaint.setStrokeWidth(3f);

                        Paint linePaint = new Paint();
                        linePaint.setColor(Color.GREEN);
                        linePaint.setStyle(Paint.Style.STROKE);
                        linePaint.setStrokeWidth(2f);

                        for (FirebaseVisionFace face : firebaseVisionFaces) {
                            List<FirebaseVisionPoint> faceContours = face.getContour(
                                    FirebaseVisionFaceContour.FACE
                            ).getPoints();
                            for (int i = 0; i < faceContours.size(); i++) {
                                FirebaseVisionPoint faceContour = null;
                                if (i != (faceContours.size() - 1)) {
                                    faceContour = faceContours.get(i);
                                    canvas.drawLine(faceContour.getX(),
                                            faceContour.getY(),
                                            faceContours.get(i + 1).getX(),
                                            faceContours.get(i + 1).getY(),
                                            linePaint

                                    );

                                } else {
                                    canvas.drawLine(faceContour.getX(),
                                            faceContour.getY(),
                                            faceContours.get(0).getX(),
                                            faceContours.get(0).getY(),
                                            linePaint);
                                }
                                canvas.drawCircle(faceContour.getX(),
                                        faceContour.getY(),
                                        4f,
                                        dotPaint );
                            }

                            List<FirebaseVisionPoint> leftEyebrowTopCountours = face.getContour(
                                    FirebaseVisionFaceContour.LEFT_EYEBROW_TOP).getPoints();
                            for (int i = 0; i < leftEyebrowTopCountours.size(); i++) {
                                FirebaseVisionPoint contour = leftEyebrowTopCountours.get(i);
                                if (i != (leftEyebrowTopCountours.size() - 1))
                                    canvas.drawLine(contour.getX(), contour.getY(), leftEyebrowTopCountours.get(i + 1).getX(),leftEyebrowTopCountours.get(i + 1).getY(), linePaint);
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> rightEyebrowTopCountours = face.getContour(
                                    FirebaseVisionFaceContour. RIGHT_EYEBROW_TOP).getPoints();
                            for (int i = 0; i < rightEyebrowTopCountours.size(); i++) {
                                FirebaseVisionPoint contour = rightEyebrowTopCountours.get(i);
                                if (i != (rightEyebrowTopCountours.size() - 1))
                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyebrowTopCountours.get(i + 1).getX(),rightEyebrowTopCountours.get(i + 1).getY(), linePaint);
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> rightEyebrowBottomCountours = face.getContour(
                                    FirebaseVisionFaceContour. RIGHT_EYEBROW_BOTTOM).getPoints();
                            for (int i = 0; i < rightEyebrowBottomCountours.size(); i++) {
                                FirebaseVisionPoint contour = rightEyebrowBottomCountours.get(i);
                                if (i != (rightEyebrowBottomCountours.size() - 1))
                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyebrowBottomCountours.get(i + 1).getX(),rightEyebrowBottomCountours.get(i + 1).getY(), linePaint);
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> leftEyeContours = face.getContour(
                                    FirebaseVisionFaceContour.LEFT_EYE).getPoints();
                            for (int i = 0; i < leftEyeContours.size(); i++) {
                                FirebaseVisionPoint contour = leftEyeContours.get(i);
                                if (i != (leftEyeContours.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(), leftEyeContours.get(i + 1).getX(),leftEyeContours.get(i + 1).getY(), linePaint);

                                }else {
                                    canvas.drawLine(contour.getX(), contour.getY(), leftEyeContours.get(0).getX(),
                                            leftEyeContours.get(0).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);


                            }

                            List<FirebaseVisionPoint> rightEyeContours = face.getContour(
                                    FirebaseVisionFaceContour.RIGHT_EYE).getPoints();
                            for (int i = 0; i < rightEyeContours.size(); i++) {
                                FirebaseVisionPoint contour = rightEyeContours.get(i);
                                if (i != (rightEyeContours.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyeContours.get(i + 1).getX(),rightEyeContours.get(i + 1).getY(), linePaint);

                                }else {
                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyeContours.get(0).getX(),
                                            rightEyeContours.get(0).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);


                            }

                            List<FirebaseVisionPoint> upperLipTopContour = face.getContour(
                                    FirebaseVisionFaceContour.UPPER_LIP_TOP).getPoints();
                            for (int i = 0; i < upperLipTopContour.size(); i++) {
                                FirebaseVisionPoint contour = upperLipTopContour.get(i);
                                if (i != (upperLipTopContour.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(),
                                            upperLipTopContour.get(i + 1).getX(),
                                            upperLipTopContour.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> upperLipBottomContour = face.getContour(
                                    FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).getPoints();
                            for (int i = 0; i < upperLipBottomContour.size(); i++) {
                                FirebaseVisionPoint contour = upperLipBottomContour.get(i);
                                if (i != (upperLipBottomContour.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(), upperLipBottomContour.get(i + 1).getX(),upperLipBottomContour.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }
                            List<FirebaseVisionPoint> lowerLipTopContour = face.getContour(
                                    FirebaseVisionFaceContour.LOWER_LIP_TOP).getPoints();
                            for (int i = 0; i < lowerLipTopContour.size(); i++) {
                                FirebaseVisionPoint contour = lowerLipTopContour.get(i);
                                if (i != (lowerLipTopContour.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(), lowerLipTopContour.get(i + 1).getX(),lowerLipTopContour.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }
                            List<FirebaseVisionPoint> lowerLipBottomContour = face.getContour(
                                    FirebaseVisionFaceContour.LOWER_LIP_BOTTOM).getPoints();
                            for (int i = 0; i < lowerLipBottomContour.size(); i++) {
                                FirebaseVisionPoint contour = lowerLipBottomContour.get(i);
                                if (i != (lowerLipBottomContour.size() - 1)){
                                    canvas.drawLine(contour.getX(), contour.getY(), lowerLipBottomContour.get(i + 1).getX(),lowerLipBottomContour.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> noseBridgeContours = face.getContour(
                                    FirebaseVisionFaceContour.NOSE_BRIDGE).getPoints();
                            for (int i = 0; i < noseBridgeContours.size(); i++) {
                                FirebaseVisionPoint contour = noseBridgeContours.get(i);
                                if (i != (noseBridgeContours.size() - 1)) {
                                    canvas.drawLine(contour.getX(), contour.getY(), noseBridgeContours.get(i + 1).getX(),noseBridgeContours.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }

                            List<FirebaseVisionPoint> noseBottomContours = face.getContour(
                                    FirebaseVisionFaceContour.NOSE_BOTTOM).getPoints();
                            for (int i = 0; i < noseBottomContours.size(); i++) {
                                FirebaseVisionPoint contour = noseBottomContours.get(i);
                                if (i != (noseBottomContours.size() - 1)) {
                                    canvas.drawLine(contour.getX(), contour.getY(), noseBottomContours.get(i + 1).getX(),noseBottomContours.get(i + 1).getY(), linePaint);
                                }
                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);

                            }
                            if (camerFacing == Facing.FRONT) {
                                //Flip image!
                                Matrix matrix = new Matrix();
                                matrix.preScale(-1f, 1f);
                                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                                        bitmap.getWidth(), bitmap.getHeight(),
                                        matrix, true);
                                imgView.setImageBitmap(flippedBitmap);
                            }else
                                imgView.setImageBitmap(bitmap);
                        }//end forloop


                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        imgView.setImageBitmap(null);

                    }
                });
    }
}