package mmdeploy;

import static mmdeploy.Camera.BgraToBgr;
import static mmdeploy.Camera.getBasePath;
import static mmdeploy.Camera.pixArrToBgra;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.blankj.utilcode.util.PathUtils;
import com.blankj.utilcode.util.ResourceUtils;
import com.bumptech.glide.Glide;
import com.example.mmdeploy.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ImageView imageview;
    private ImageView sampleOne;
    private ImageView sampleTwo;
    private ImageView sampleThree;
    private Spinner spinner;
    private Button btn_run;
    private Uri imageUri;

    private int current_model = 0;
    private Detector detector;
    private MultiBoxTracker tracker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageview = this.findViewById(R.id.imageView);
        imageview.setOnTouchListener(new TouchListener());

        sampleOne = this.findViewById(R.id.imgSampleOne);
        sampleTwo = this.findViewById(R.id.imgSampleTwo);
        sampleThree = this.findViewById(R.id.imgSampleThree);
        btn_run = this.findViewById(R.id.btn_run);
        tracker = new MultiBoxTracker(MainActivity.this);

        String[] models = {"mobilessd", "yoloV3", "yoloX"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner_item, models);
        spinner = this.findViewById(R.id.spinner);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                current_model = (int) spinner.getSelectedItemId();
                initMMDepoly();

                String str = parent.getItemAtPosition(position).toString();
                Toast.makeText(MainActivity.this, "已经加载" + str + "模型数据", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private final class TouchListener implements View.OnTouchListener {

        /** 记录是拖拉照片模式还是放大缩小照片模式 */
        private int mode = 0;// 初始状态
        /** 拖拉照片模式 */
        private static final int MODE_DRAG = 1;
        /** 放大缩小照片模式 */
        private static final int MODE_ZOOM = 2;

        /** 用于记录开始时候的坐标位置 */
        private PointF startPoint = new PointF();
        /** 用于记录拖拉图片移动的坐标位置 */
        private Matrix matrix = new Matrix();
        /** 用于记录图片要进行拖拉时候的坐标位置 */
        private Matrix currentMatrix = new Matrix();

        /** 两个手指的开始距离 */
        private float startDis;
        /** 两个手指的中间点 */
        private PointF midPoint;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            /** 通过与运算保留最后八位 MotionEvent.ACTION_MASK = 255 */
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                // 手指压下屏幕
                case MotionEvent.ACTION_DOWN:
                    mode = MODE_DRAG;
                    // 记录ImageView当前的移动位置
                    currentMatrix.set(imageview.getImageMatrix());
                    startPoint.set(event.getX(), event.getY());
                    break;
                // 手指在屏幕上移动，改事件会被不断触发
                case MotionEvent.ACTION_MOVE:
                    // 拖拉图片
                    if (mode == MODE_DRAG) {
                        float dx = event.getX() - startPoint.x; // 得到x轴的移动距离
                        float dy = event.getY() - startPoint.y; // 得到x轴的移动距离
                        // 在没有移动之前的位置上进行移动
                        matrix.set(currentMatrix);
                        matrix.postTranslate(dx, dy);
                    }
                    // 放大缩小图片
                    else if (mode == MODE_ZOOM) {
                        float endDis = distance(event);// 结束距离
                        if (endDis > 10f) { // 两个手指并拢在一起的时候像素大于10
                            float scale = endDis / startDis;// 得到缩放倍数
                            matrix.set(currentMatrix);
                            matrix.postScale(scale, scale,midPoint.x,midPoint.y);
                        }
                    }
                    break;
                // 手指离开屏幕
                case MotionEvent.ACTION_UP:
                    // 当触点离开屏幕，但是屏幕上还有触点(手指)
                case MotionEvent.ACTION_POINTER_UP:
                    mode = 0;
                    break;
                // 当屏幕上已经有触点(手指)，再有一个触点压下屏幕
                case MotionEvent.ACTION_POINTER_DOWN:
                    mode = MODE_ZOOM;
                    /** 计算两个手指间的距离 */
                    startDis = distance(event);
                    /** 计算两个手指间的中间点 */
                    if (startDis > 10f) { // 两个手指并拢在一起的时候像素大于10
                    midPoint = mid(event);
                    //记录当前ImageView的缩放倍数
                    currentMatrix.set(imageview.getImageMatrix());
                }
                break;
            }
            imageview.setImageMatrix(matrix);
            return true;
        }

        /** 计算两个手指间的距离 */
        private float distance(MotionEvent event) {
            float dx = event.getX(1) - event.getX(0);
            float dy = event.getY(1) - event.getY(0);
            /** 使用勾股定理返回两点之间的距离 */
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        /** 计算两个手指间的中间点 */
        private PointF mid(MotionEvent event) {
            float midX = (event.getX(1) + event.getX(0)) / 2;
            float midY = (event.getY(1) + event.getY(0)) / 2;
            return new PointF(midX, midY);
        }

    }

    public void openAlbum(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, 222);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 222 && data != null){
            Glide.with(this).load(data.getData()).into(imageview);
            btn_run.setVisibility(View.VISIBLE);
        }

        if (requestCode == 123){
            Glide.with(this).load(imageUri).into(imageview);
            btn_run.setVisibility(View.VISIBLE);
        }
    }

    public void openCamera(View view){
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 333);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 333){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Intent intent = new Intent((MediaStore.ACTION_IMAGE_CAPTURE));
                File file = new File(getCacheDir().getAbsolutePath()+System.currentTimeMillis()+".jpg");

                if(!file.exists()){
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                imageUri = FileProvider.getUriForFile(this,"com.example.mmdeploy.provider",file);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this,"com.example.mmdeploy.provider",file));
                startActivityForResult(intent, 123);
            }
        }
    }

    public void openCameraLive(View view) {
        Intent intent = new Intent(this, Camera.class);
        startActivity(intent);
    }

    public static String getBasePath() {
        return PathUtils.getExternalAppFilesPath() + File.separator + "file";
    }

    private void reload(String workDir, int modelID){
        String[] modelTypes = {"mobilessd", "yolo", "yolox"};
        String modelPath = workDir + "/" + modelTypes[(int)modelID];
        String deviceName = "cpu";
        int deviceID = 0;
        this.detector = new Detector(modelPath, deviceName, deviceID);
    }

    private void initMMDepoly(){
        String workDir = getBasePath();
        if (ResourceUtils.copyFileFromAssets("dump_info", workDir)){
            reload(workDir, current_model);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void runObjectDetection(View view) {
        Bitmap bitmap = ((BitmapDrawable)imageview.getDrawable()).getBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixArr = new int[width*height];

        bitmap.getPixels(pixArr, 0, width, 0, 0, width, height);
        byte[] bgra = pixArrToBgra(pixArr, width, height);
        byte[] data = BgraToBgr(bgra, width, height);
        Mat rgb = new Mat(height, width, 3, PixelFormat.BGR, DataType.INT8, data);
        Detector.Result[] result = detector.apply(rgb);

        List<Detector.Result> results = Arrays.asList(result);
        List<Detector.Result> collect = results.parallelStream().filter(result1 -> result1.getScore() >= 0.3).collect(Collectors.toList());

        tracker.setFrameConfiguration(width, height, 0);
        tracker.trackResults(collect);

        Bitmap outputBitMap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(outputBitMap);
        tracker.draw(canvas);
        imageview.setImageBitmap(outputBitMap);

        btn_run.setVisibility(View.INVISIBLE);
        Toast.makeText(getApplicationContext(), "推理成功", Toast.LENGTH_SHORT).show();
    }

    public void changeImageOne(View view) {
        Bitmap bitmap = ((BitmapDrawable)sampleOne.getDrawable()).getBitmap();
        imageview.setImageBitmap(bitmap);
        btn_run.setVisibility(View.VISIBLE);
    }

    public void changeImageTwo(View view) {
        Bitmap bitmap = ((BitmapDrawable)sampleTwo.getDrawable()).getBitmap();
        imageview.setImageBitmap(bitmap);
        btn_run.setVisibility(View.VISIBLE);
    }

    public void changeImageThree(View view) {
        Bitmap bitmap = ((BitmapDrawable)sampleThree.getDrawable()).getBitmap();
        imageview.setImageBitmap(bitmap);
        btn_run.setVisibility(View.VISIBLE);
    }
}