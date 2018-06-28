package elite.driver.adsmove.com.per2_listen;

import android.*;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;

import static android.content.ContentValues.TAG;
import static android.media.AudioTrack.WRITE_BLOCKING;
import static android.media.AudioTrack.WRITE_NON_BLOCKING;

public class MainActivity extends AppCompatActivity {

    //Database firebase
    private FirebaseDatabase db = FirebaseDatabase.getInstance("https://livedata-72b29.firebaseio.com/");

    //Đường truyền 1: A nói B nghe
    private DatabaseReference per1 = db.getReference("per1_audio");
    //Đường truyền 2: B nói A nghe
    private DatabaseReference per2 = db.getReference("per2_audio");

    //Config AudioRecord & AudioTrack
    private static final int RECORDER_SAMPLERATE = 18000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int RECORDER_MANAGER_STEAM = AudioManager.STREAM_VOICE_CALL;
    private static final int RECORDER_TRACK_STEAM = AudioTrack.MODE_STREAM;
    int mBufferSize = 1024;

    // Call audio
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private int cAmplitude = 0;
    private int read = 0;

    //Palate
    private Button On;
    private Button Off;

    // Listen audio
    private String mBitmapBase64 = null;
    private Dialog dialog;
    private byte[] decodedBytes = new byte[mBufferSize];
    private AudioTrack audio;
    private AudioManager audioManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        On = (Button) findViewById(R.id.on);
        On.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogShow();
            }
        });

        Off = (Button) findViewById(R.id.off);
        Off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Off.setEnabled(false);
                On.setEnabled(true);

                stopRecording();
                stopPlayAudio();
            }
        });

        requestRecordAudioPermission();

        // Audio Track config
        initAudioTrack();

    }

    private void requestRecordAudioPermission() {

        String requiredPermission = Manifest.permission.RECORD_AUDIO;

        // If the user previously denied this permission then show a message explaining why
        // this permission is needed
        if (getApplication().checkCallingOrSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED) {

        } else {

            Toast.makeText(getApplication(), "Bạn có cho phép truy cập vào Micro Audio....", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{requiredPermission}, 101);
            }
        }
    }

    public void initAudioTrack() {

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamVolume(RECORDER_MANAGER_STEAM,audioManager.getStreamMaxVolume(RECORDER_MANAGER_STEAM),0);
        audioManager.setMode(RECORDER_MANAGER_STEAM);
        audioManager.setParameters("noise_suppression=on");
        // NoiseSuppressor.create(audioManager.generateAudioSessionId());
        // AutomaticGainControl.create(audioManager.generateAudioSessionId());
        // AcousticEchoCanceler.create(audioManager.generateAudioSessionId());
      //  audioManager.setSpeakerphoneOn(false);
      //  audioManager.setMode(AudioManager.MODE_IN_CALL);
      //  audioManager.setRouting(AudioManager.MODE_NORMAL,AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);

        // Audio Track config
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, mBufferSize*20);

        audio = new AudioTrack(RECORDER_MANAGER_STEAM,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                mBufferSize*20,  // use a buffer twice the size of
                RECORDER_TRACK_STEAM);      // the chunks you will write to it

        //   audio.setPlaybackHeadPosition(100);
        audio.setPlaybackRate(RECORDER_SAMPLERATE+1000);
      //  audio.play();
    }

    private void releaseAudioTrack() {
        if (this.audio != null) {
            Log.d(TAG, "Stopping");
            audio.stop();
            Log.d(TAG, "Releasing");
            audio.release();
            Log.d(TAG, "Nulling");
        }
    }

    private void dataChangeServer() {
        per2.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                try {
                    if (null != audio) {
                        audio.flush();
                    }
                    mBitmapBase64 = dataSnapshot.getValue(String.class);
                    if (mBitmapBase64 != null) {

                        //Decode String To Audio With mig +Base64.
                        decodedBytes = Base64.decode(mBitmapBase64, Base64.DEFAULT);
                     //   Log.w(TAG, "mBitmapBase64: " + mBitmapBase64);

                    //    long beforeTime = System.nanoTime();
                        audio.write(decodedBytes, 0, decodedBytes.length);
                        audio.play();
                     //   Log.e("WriteToPlayerThread", "Writing a package took " + (System.nanoTime() - beforeTime) + " milliseconds");
                        //audio.flush();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Khong doc duoc du lieu.", error.toException());
            }
        });
    }

    private void dialogShow() {
        dialog = new Dialog(this); // Context, this, etc.
        dialog.setContentView(R.layout.dialog_layout_1);
        dialog.setTitle(R.string.dialog_thongbao);
        Button btn_yes = dialog.findViewById(R.id.btn_yes);
        btn_yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                On.setEnabled(false);
                Off.setEnabled(true);
                startRecording();
                dialog.dismiss();
            }
        });
        Button btn_no = dialog.findViewById(R.id.btn_no);
        btn_no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void startRecording() {

        dataChangeServer();

        if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
            recorder.startRecording();
            isRecording = true;
            isRecording = true;
            recordingThread = new Thread(new Runnable() {
                public void run() {
                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");
            recordingThread.start();
        } else {
            Log.d(TAG, "Audio recorder failed to initialise");
        }
    }

    private short getShort(byte argB1, byte argB2) {
        return (short) (argB1 | (argB2 << 8));
    }

    private void writeAudioDataToFile() {
        // Write the output audio in byte
        byte sData[] = new byte[mBufferSize];

        while (isRecording) {
            // gets the voice output from microphone to byte format
            read = recorder.read(sData, 0, sData.length);

            for (int i = 0; i < read / 2; i++) {
                short curSample = getShort(sData[i * 2], sData[i * 2 + 1]);
                if (curSample > cAmplitude) {
                    cAmplitude = curSample;
                }
            }
            cAmplitude = 0;

            String _encoder = Base64.encodeToString(sData, Base64.DEFAULT);
            //       Log.d(TAG, "String_Audio_Base64: " + _encoder);
            //    String encoder = bb_to_str(encodedData, Charset.defaultCharset());
            if (_encoder != null) {
                per1.setValue(_encoder, new DatabaseReference.CompletionListener() {
                    @Override
                    public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                        Log.d(TAG, "upload ERROR: " + databaseError);
                    }
                });
            }

        }

    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }

    private void stopPlayAudio() {
        // stops the play audio activity
        if (null != audio) {
            audio.stop();
            audio.release();
            audio = null;
        }
    }
}
