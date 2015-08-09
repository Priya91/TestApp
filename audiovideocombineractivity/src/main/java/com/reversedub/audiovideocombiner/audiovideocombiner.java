package com.reversedub.audiovideocombiner;

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class audiovideocombiner extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audiovideocombiner);

        final Button button = (Button) findViewById(R.id.button_id);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final TextView textView = (TextView) findViewById(R.id.textView_id);
                textView.setText("");
                Boolean result = CombineFilesUsingMp4Parser("/sdcard/reversedub/video.mp4", "/sdcard/reversedub/audio.m4a", "/sdcard/reversedub/output.mp4");
                if (result) {
                    textView.setText("Output file: /sdcard/reversedub/output.mp4");
                } else {
                    textView.setText("Operation failed.");
                }
            }
        });
    }

    // Doesn't work, debugging in progress
    private Boolean CombineFilesUsingMediaMuxer(String videoFile, String audioFile, String outputFile)
    {
        MediaMuxer muxer;
        try
        {
            muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        MediaExtractor mediaVideoExtractor = new MediaExtractor();
        try
        {
            mediaVideoExtractor.setDataSource(videoFile);
        } catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }

        MediaFormat videoFormat = mediaVideoExtractor.getTrackFormat(0);
        mediaVideoExtractor.selectTrack(0);

        MediaExtractor mediaAudioExtractor = new MediaExtractor();
        try
        {
            mediaAudioExtractor.setDataSource(audioFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        MediaFormat audioFormat = mediaAudioExtractor.getTrackFormat(0);
        mediaAudioExtractor.selectTrack(0);

        int videoTrackIndex = muxer.addTrack(videoFormat);
        int audioTrackIndex = muxer.addTrack(audioFormat);

        ByteBuffer inputBuffer = ByteBuffer.allocate(1000000);
        boolean finished = false;
        boolean isAudioSample = false;
        int offset = 0;
        BufferInfo bufferInfo = new BufferInfo();

        muxer.start();
        while(!finished) {

            if (isAudioSample)
            {
                bufferInfo.offset = offset;
                bufferInfo.size = mediaAudioExtractor.readSampleData(inputBuffer, offset);
                bufferInfo.flags = mediaAudioExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs = mediaAudioExtractor.getSampleTime();
                mediaAudioExtractor.advance();
            }
            else
            {
                bufferInfo.offset = offset;
                bufferInfo.size = mediaVideoExtractor.readSampleData(inputBuffer, offset);
                bufferInfo.flags = mediaVideoExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs = mediaVideoExtractor.getSampleTime();
                mediaVideoExtractor.advance();
            }

            if (bufferInfo.size < 0)
            {
                finished = true;
                bufferInfo.size = 0;

                mediaAudioExtractor.release();
                mediaVideoExtractor.release();

                mediaAudioExtractor = null;
                mediaVideoExtractor = null;
            }

            if (!finished) {
                int currentTrackIndex = isAudioSample ? audioTrackIndex : videoTrackIndex;
                muxer.writeSampleData(currentTrackIndex, inputBuffer, bufferInfo);
                isAudioSample = !isAudioSample;
            }
        };
        muxer.stop();
        muxer.release();
        return true;
    }

    private Boolean CombineFilesUsingMp4Parser(String videoFile, String audioFile, String outputFile)
    {
        Movie video;
        try {
            video = new MovieCreator().build(videoFile);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        Movie audio;
        try {
            audio = new MovieCreator().build(audioFile);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (NullPointerException e) {
            e.printStackTrace();
            return false;
        }

        Track audioTrack = audio.getTracks().get(0);
        video.addTrack(audioTrack);

        Container out = new DefaultMp4Builder().build(video);

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        BufferedWritableFileByteChannel byteBufferByteChannel = new BufferedWritableFileByteChannel(fos);
        try {
            out.writeContainer(byteBufferByteChannel);
            byteBufferByteChannel.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static class BufferedWritableFileByteChannel implements WritableByteChannel {
        private static final int BUFFER_CAPACITY = 1000000;

        private boolean isOpen = true;
        private final OutputStream outputStream;
        private final ByteBuffer byteBuffer;
        private final byte[] rawBuffer = new byte[BUFFER_CAPACITY];

        private BufferedWritableFileByteChannel(OutputStream outputStream) {
            this.outputStream = outputStream;
            this.byteBuffer = ByteBuffer.wrap(rawBuffer);
        }

        @Override
        public int write(ByteBuffer inputBuffer) throws IOException {
            int inputBytes = inputBuffer.remaining();

            if (inputBytes > byteBuffer.remaining()) {
                dumpToFile();
                byteBuffer.clear();

                if (inputBytes > byteBuffer.remaining()) {
                    throw new BufferOverflowException();
                }
            }

            byteBuffer.put(inputBuffer);

            return inputBytes;
        }

        @Override
        public boolean isOpen() {
            return isOpen;
        }

        @Override
        public void close() throws IOException {
            dumpToFile();
            isOpen = false;
        }
        private void dumpToFile() {
            try {
                outputStream.write(rawBuffer, 0, byteBuffer.position());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_audiovideocombiner, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
