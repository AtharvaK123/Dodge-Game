package com.example.dodgegame;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.dodgegame.R;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    // Declare GameSurface Instance Defined in Inner Class
    GameSurface gameSurface;

    int soundId;
    private TextView timerTextView;
    private TextView scoreTextView;  // Add this line
    private int secondsElapsed;
    private Handler timerHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize GameSurface in Context (In this case, the MainActivity)
        gameSurface = new GameSurface(this);

        // Create a FrameLayout to hold the GameSurface and TextViews
        FrameLayout gameLayout = new FrameLayout(this);
        gameLayout.addView(gameSurface);

        // Initialize and configure the timer TextView
        timerTextView = new TextView(this);
        timerTextView.setTextSize(24);
        timerTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams timerLayoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        timerLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        gameLayout.addView(timerTextView, timerLayoutParams);

        // Initialize and configure the score TextView
        scoreTextView = new TextView(this);
        scoreTextView.setTextSize(24);
        scoreTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams scoreLayoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        scoreLayoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        scoreLayoutParams.topMargin = 50; // Adjust margin to avoid overlap with timer
        gameLayout.addView(scoreTextView, scoreLayoutParams);

        // Set the FrameLayout as the content view
        setContentView(gameLayout);

        // Start the timer
        startTimer();
    }

    private void startTimer() {
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                secondsElapsed++;
                timerTextView.setText("Time: " + secondsElapsed);
                timerHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    // LifeCycle Methods employed to call the GameSurface pause/resume and therefore
    // ensure our game does not crash if/when the application is paused/resumed
    @Override
    protected void onResume() {
        super.onResume();
        gameSurface.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameSurface.pause();
    }

    // Define GameSurface
    public class GameSurface extends SurfaceView implements Runnable, SensorEventListener {
        // Almost all of these variables are required anytime you are implementing a SurfaceView
        Thread gameThread;  // required for functionality
        SurfaceHolder holder; // required for functionality
        volatile boolean running = false; // variable shared amongst threads; required for functionality
        Bitmap ball, background, obstacles;
        int ballX, obstacleY, score, r;
        Paint paintProperty; // required for functionality
        int screenWidth, screenHeight; // required for functionality
        float totalFlip = 0f;
        boolean done = false;
        MediaPlayer mediaPlayer;
        SoundPool soundPool;
        Rect ballRect, obstacleRect; // Add Rect objects for collision detection

        public GameSurface(Context context) {
            super(context);
            // Initialize holder
            holder = getHolder();

            score = 0;

            // Initialize resources
            background = BitmapFactory.decodeResource(getResources(), R.drawable.chess);
            ball = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chessking),
                    200, 200, false);
            obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chesspawn),
                    200, 200, false);

            // Initialize Rect objects
            ballRect = new Rect();
            obstacleRect = new Rect();

            Timer t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int choosePiece = (int) (Math.random() * 5) + 1;
                            if (choosePiece == 1) {
                                obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chesspawn),
                                        200, 200, false);
                            }
                            if (choosePiece == 2) {
                                obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chessbishop),
                                        200, 200, false);
                            }
                            if (choosePiece == 3) {
                                obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chessknight),
                                        200, 200, false);
                            }
                            if (choosePiece == 4) {
                                obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chessrook),
                                        200, 200, false);
                            }
                            if (choosePiece == 5) {
                                obstacles = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.chessqueen),
                                        200, 200, false);
                            }
                        }
                    });
                }
            }, 0, 3000);

            // Retrieve screensize
            Display screenDisplay = getWindowManager().getDefaultDisplay();
            Point sizeOfScreen = new Point();
            screenDisplay.getSize(sizeOfScreen);
            screenWidth = sizeOfScreen.x;
            screenHeight = sizeOfScreen.y;

            // Initialize paintProperty for "drawing" on the Canvas
            paintProperty = new Paint();

            // Needed if using MotionSensors to create the image movement
            // can remove the next 3 lines if not using a listener and just want a continuous ball movement
            SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // to create movement based on change in z-axis
            // added a *-1 so it moves in direction of phone tilt instead of invertedly
            totalFlip = event.values[0] * -1;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        // Create run method for thread
        @Override
        public void run() {
            // Create Canvas to "draw" on
            Canvas canvas = null;
            // Put resource into a referencable Drawable
            Drawable d = getResources().getDrawable(R.drawable.chessy, null);

            mediaPlayer = MediaPlayer.create(this.getContext(), R.raw.chesstheme);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();

            int flip = 5;
            // loop should run as long as running == true
            while (running) {
                // if holder is null or invalid, exit loop
                if (!holder.getSurface().isValid())
                    continue;

                // lock canvas to make necessary changes
                canvas = holder.lockCanvas(null);

                // resize background drawable to the root View left/top/right/bottom
                d.setBounds(getLeft(), getTop(), getRight(), getBottom());

                // draw the Drawable onto the canvas
                d.draw(canvas);

                // Define the spacing required to accommodate the image and screen size so images don't exceed bounds
                float ballImageHorizontalSpacing = (screenWidth / 2.0f) - (ball.getWidth() / 2.0f);
                float obstacleImageVerticalSpacing = 0;

                // draw ball onto Canvas
                canvas.drawBitmap(ball, ballImageHorizontalSpacing + ballX, 1900, null);
                if (done == false) {
                    r = (int) (Math.random() * 1000) + 1;
                    done = true;
                }
                Log.d("", String.valueOf(score));
                canvas.drawBitmap(obstacles, r, obstacleImageVerticalSpacing + obstacleY, null);

                // Update the positions of the Rect objects
                ballRect.set((int) ballImageHorizontalSpacing + ballX, 1900,
                        (int) ballImageHorizontalSpacing + ballX + ball.getWidth(), 1900 + ball.getHeight());
                obstacleRect.set(r, (int) (obstacleImageVerticalSpacing + obstacleY),
                        r + obstacles.getWidth(), (int) (obstacleImageVerticalSpacing + obstacleY + obstacles.getHeight()));

                // Check for collisions
                if (Rect.intersects(ballRect, obstacleRect)) {
                    score++;
                    updateScore();
                    obstacleY = screenHeight; // Move the obstacle out of the screen to reset its position
                }

                if (obstacleY < 2000) {
                    obstacleY += 5;
                } else {
                    obstacleY = 0;
                    done = false;
                }

                // With Sensors
                if (ballX + totalFlip < (int) ballImageHorizontalSpacing && ballX + totalFlip > -1 * (int) ballImageHorizontalSpacing) {
                    ballX += totalFlip;
                }

                if ((obstacleImageVerticalSpacing + obstacleY) > 2000) {
                    obstacles.recycle();
                }

                holder.unlockCanvasAndPost(canvas);
            }
        }

        private void updateScore() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    scoreTextView.setText("Score: " + score);
                }
            });
        }

        public void resume() {
            running = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        public void pause() {
            running = false;
            while (true) {
                try {
                    gameThread.join();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
