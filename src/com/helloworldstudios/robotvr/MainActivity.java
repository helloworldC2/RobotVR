

package com.helloworldstudios.robotvr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.helloworldstudios.robotvr.net.GameServer;
import com.helloworldstudios.robotvr.util.Camera;

/**
 * A Cardboard sample application.
 */
public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer {

    private static final String TAG = "MainActivity";

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.3f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    // We keep the light always position just above the user.
    private final float[] mLightPosInWorldSpace = new float[] {0.0f, 2.0f, 0.0f, 1.0f};
    private final float[] mLightPosInEyeSpace = new float[4];

    public static final int COORDS_PER_VERTEX = 3;

    private final WorldLayoutData DATA = new WorldLayoutData();
    private Level level;
    private FloatBuffer mFloorVertices;
    private FloatBuffer mFloorColors;
    private FloatBuffer mFloorNormals;

    public FloatBuffer mCubeVertices;
    public FloatBuffer mCubeColors;
    public FloatBuffer mCubeFoundColors;
    public FloatBuffer mCubeNormals;
    public FloatBuffer mCubeTextureCoordinates;
     
    /** This will be used to pass in the texture. */
    public int mTextureUniformHandle;
     
    /** This will be used to pass in model texture coordinate information. */
    public int mTextureCoordinateHandle;
     
    /** Size of the texture coordinate data in elements. */
    public final int mTextureCoordinateDataSize = 2;
     
    /** This is a handle to our texture data. */
    public int mTextureDataHandle;

    public int mGlProgram;
    public int mPositionParam;
    public int mNormalParam;
    public int mColorParam;
    public int mModelViewProjectionParam;
    public int mLightPosParam;
    public int mModelViewParam;
    public int mModelParam;
    public int mIsFloorParam;

    public float[] mModelCube;
    private float[] mCamera;
    private float[] mView;
    private float[] mHeadView;
    public float[] mModelViewProjection;
    public float[] mModelView;

    private float[] mModelFloor;

    private int mScore = 0;
   
    private float mFloorDepth = 20f;

    private Vibrator mVibrator;

    private CardboardOverlayView mOverlayView;
    
    public Camera camera;
    
    private GameServer server;

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        CardboardView cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mModelCube = new float[16];
        mCamera = new float[16];
        mView = new float[16];
        mModelViewProjection = new float[16];
        mModelView = new float[16];
        mModelFloor = new float[16];
        mHeadView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);


        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Look through the eyes of the robot!");
        camera = new Camera();
        server = new GameServer(this);
        server.start();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well
        level = new Level(this);
        ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.CUBE_COORDS.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        mCubeVertices = bbVertices.asFloatBuffer();
        mCubeVertices.put(DATA.CUBE_COORDS);
        mCubeVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(DATA.CUBE_COLORS.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        mCubeColors = bbColors.asFloatBuffer();
        mCubeColors.put(DATA.CUBE_COLORS);
        mCubeColors.position(0);

        ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(DATA.CUBE_FOUND_COLORS.length * 4);
        bbFoundColors.order(ByteOrder.nativeOrder());
        mCubeFoundColors = bbFoundColors.asFloatBuffer();
        mCubeFoundColors.put(DATA.CUBE_FOUND_COLORS);
        mCubeFoundColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(DATA.CUBE_NORMALS.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        mCubeNormals = bbNormals.asFloatBuffer();
        mCubeNormals.put(DATA.CUBE_NORMALS);
        mCubeNormals.position(0);
        
        mCubeTextureCoordinates = ByteBuffer.allocateDirect(DATA.CUBE_TEXCOORDS.length * 4)
        		.order(ByteOrder.nativeOrder()).asFloatBuffer();
        		mCubeTextureCoordinates.put(DATA.CUBE_TEXCOORDS).position(0);

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        mFloorVertices = bbFloorVertices.asFloatBuffer();
        mFloorVertices.put(DATA.FLOOR_COORDS);
        mFloorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        mFloorNormals = bbFloorNormals.asFloatBuffer();
        mFloorNormals.put(DATA.FLOOR_NORMALS);
        mFloorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        mFloorColors = bbFloorColors.asFloatBuffer();
        mFloorColors.put(DATA.FLOOR_COLORS);
        mFloorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);

        mGlProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mGlProgram, vertexShader);
        GLES20.glAttachShader(mGlProgram, gridShader);
        GLES20.glLinkProgram(mGlProgram);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Object first appears directly in front of user
        Matrix.setIdentityM(mModelCube, 0);
        

        Matrix.setIdentityM(mModelFloor, 0);
        Matrix.translateM(mModelFloor, 0, 0, 0, 0);

        checkGLError("onSurfaceCreated");
    }

    /**
     * Converts a raw text file into a string.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    public String readRawTextFile(int resId) {
        InputStream inputStream = getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    public static int loadTexture(final Context context, final int resourceId)
    {
        final int[] textureHandle = new int[1];
     
        GLES20.glGenTextures(1, textureHandle, 0);
     
        if (textureHandle[0] != 0)
        {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;   // No pre-scaling
     
            // Read in the resource
            final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
     
            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
     
            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
     
            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
     
            // Recycle the bitmap, since its data has been loaded into OpenGL.
            bitmap.recycle();
        }
     
        if (textureHandle[0] == 0)
        {
            throw new RuntimeException("Error loading texture.");
        }
     
        return textureHandle[0];
    }
    
    
    /**
     * Prepares OpenGL ES before we draw a frame.
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        GLES20.glUseProgram(mGlProgram);

        mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
        mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
        mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
        mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
        mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");

        

        // Build the camera matrix and apply it to the ModelView.
        Matrix.setLookAtM(mCamera, 0, 0, 0, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        Matrix.translateM(mCamera, 0, camera.pos.x, -1, camera.pos.y);
        headTransform.getHeadView(mHeadView, 0);
        checkGLError("onReadyToDraw");
    }

    /**
     * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
     * a parameter.
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
        mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mGlProgram, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mGlProgram, "a_TexCoordinate");
        
        GLES20.glEnableVertexAttribArray(mPositionParam);
        GLES20.glEnableVertexAttribArray(mNormalParam);
        GLES20.glEnableVertexAttribArray(mColorParam);
        checkGLError("mColorParam");

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);

        // Set the position of the light
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
        GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
                mLightPosInEyeSpace[2]);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelCube, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);
        //drawCube();
        level.render(mModelViewProjection);
        // Set mModelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0,
            mModelView, 0);
        drawFloor(transform.getPerspective());
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    

    /**
     * Draw the floor. This feeds in data for the floor into the shader. Note that this doesn't
     * feed in data about position of the light, so if we rewrite our code to draw the floor first,
     * the lighting might look strange.
     */
    public void drawFloor(float[] perspective) {
        // This is the floor!
        GLES20.glUniform1f(mIsFloorParam, 1f);

        // Set ModelView, MVP, position, normals, and color
        GLES20.glUniformMatrix4fv(mModelParam, 1, false, mModelFloor, 0);
        GLES20.glUniformMatrix4fv(mModelViewParam, 1, false, mModelView, 0);
        GLES20.glUniformMatrix4fv(mModelViewProjectionParam, 1, false, mModelViewProjection, 0);
        GLES20.glVertexAttribPointer(mPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, mFloorVertices);
        GLES20.glVertexAttribPointer(mNormalParam, 3, GLES20.GL_FLOAT, false, 0, mFloorNormals);
        GLES20.glVertexAttribPointer(mColorParam, 4, GLES20.GL_FLOAT, false, 0, mFloorColors);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("drawing floor");
    }

    /**
     * Increment the score, hide the object, and give feedback if the user pulls the magnet while
     * looking at the object. Otherwise, remind the user what to do.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        if (isLookingAtObject()) {
            mScore++;
            //mOverlayView.show3DToast("Found it! Look around for another one.\nScore = " + mScore);
           
        } else {
            //mOverlayView.show3DToast("Look around to find the object!");
        }
        // Always give user feedback
        mVibrator.vibrate(50);
    }

    

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     * @return
     */
    private boolean isLookingAtObject() {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);

        float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);

        Log.i(TAG, "Object position: X: " + objPositionVec[0]
                + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
        Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);

        return (Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT);
    }
}
