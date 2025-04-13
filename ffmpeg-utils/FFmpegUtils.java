
import com.chdctc.XX.dto.ExportVideoDto;
import com.chdctc.XX.dto.ResourceDataDto;
import com.chdctc.XX.exception.XXException;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
public class FFmpegUtils {

    private static final int DEFAULT_CHANNELS = 1;
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int DEFAULT_FRAME_RATE = 16000;
    private static final int DEFAULT_AUDIO_BITRATE = 256000;
    private static final String DEFAULT_FORMAT = "wav";


    private static final List<String> acceptedAudioFormats = List.of("mp3", "wav", "aac", "flac");

    public static List<File> getChunks(String audioUrl){
        try {
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(audioUrl);
            grabber.start(); //application crashes here

            List<File> chunks = new ArrayList<>();

            long currentStart = 0;
            while (true){
                File file = File.createTempFile("audio", ".wav");
                chunks.add(file);

                FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file, 1);
                recorder.setAudioCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE);
                recorder.setSampleRate(16000);
                recorder.setFrameRate(16000);
                recorder.setFormat("wav");
                recorder.setAudioBitrate(16);
                recorder.start();


                Frame frame;
                while ((frame = grabber.grabSamples()) != null && grabber.getTimestamp()<=currentStart+4_000_000*60){
                    recorder.record(frame);
                }

                currentStart += grabber.getTimestamp();

                if(frame == null){
                    grabber.stop();
                    grabber.close();
                    recorder.stop();
                    recorder.close();
                    break;
                }

                recorder.stop();
                recorder.close();

            }

            return chunks;
        }catch (Exception e) {
            log.error("Error while splitting audio", e);
            throw new XXException(e);
        }

    }


    public static File extractAudio(String videoUrl, int channels, int sampleRate, int frameRate, int audioBitrate, String format) throws IOException, InterruptedException {
        File file = File.createTempFile("audio", ".wav");
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);
        grabber.start();
        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file, channels);
        recorder.setAudioCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE);
        recorder.setSampleRate(sampleRate);
        recorder.setFrameRate(frameRate);
        recorder.setAudioBitrate(audioBitrate);
        recorder.setFormat(format);
        recorder.start();


        Frame frame;
        while ((frame = grabber.grabSamples()) != null){
            if(ThreadExecutionUtils.isThreadInterrupted()){
                recorder.stop();
                grabber.stop();
                recorder.close();
                grabber.close();
                log.info("Job interrupted while extracting audio");
                ThreadExecutionUtils.launchInterruptedException();
            }
            recorder.record(frame);
        }


        recorder.stop();
        grabber.stop();
        recorder.close();
        grabber.close();

        ThreadExecutionUtils.checkThreadExecution();
        return file;
    }

    public static File extractAudio(String videoUrl, String format) throws IOException, InterruptedException {
        return extractAudio(videoUrl,DEFAULT_CHANNELS, DEFAULT_SAMPLE_RATE, DEFAULT_FRAME_RATE, DEFAULT_AUDIO_BITRATE, format);
    }

    public static File extractAudio(String videoUrl) throws IOException, InterruptedException {
        return extractAudio(videoUrl,DEFAULT_CHANNELS, DEFAULT_SAMPLE_RATE, DEFAULT_FRAME_RATE, DEFAULT_AUDIO_BITRATE, DEFAULT_FORMAT);
    }

    public static File cutVideoKeepInputOptions(String videoUrl, List<Long[]> intevals, boolean fast) throws IOException {
        return cutVideo(videoUrl, intevals, true, fast, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }


    public static File cutVideo(String videoUrl, ExportVideoDto exportVideoDto, List<Long[]> intervals, boolean fast) throws Exception{
        ThreadExecutionUtils.checkThreadExecution();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl) ;
        grabber.start();

        FFmpegFrameRecorder recorder;
        File file;

        String exportFormat = exportVideoDto.getFormat().toLowerCase();
        int outputAudioCodec;
        String format = null;

        int audioChannels;
        String exportAudioChannels = exportVideoDto.getAudioChannels();
        switch (exportAudioChannels) {
            case "original" -> audioChannels = grabber.getAudioChannels();
            case "mono" -> audioChannels = 1;
            case "stereo" -> audioChannels = 2;
            default -> throw new XXException("Invalid audio channels");
        }

        int audioBitRate;
        String exportAudioBitRate = exportVideoDto.getAudioBitRate();
        switch (exportAudioBitRate) {
            case "original" -> audioBitRate = grabber.getAudioBitrate();
            case "lower" -> audioBitRate = (int) (grabber.getAudioBitrate() * 0.75);
            case "higher" -> audioBitRate = (int) (grabber.getAudioBitrate() * 1.25);
            default -> throw new XXException("Invalid audio bit rate");
        }

        int sampleRate;
        String exportSampleRate = exportVideoDto.getSampleRate();
        switch (exportSampleRate) {
            case "original" -> sampleRate = grabber.getSampleRate();
            case "48kHz" -> sampleRate = 48000;
            case "88.2kHz" -> sampleRate = 88200;
            default -> throw new XXException("Invalid sample rate");
        }


        boolean onlyAudio = exportVideoDto.isOnlyAudio();
        if(onlyAudio){
            if(exportFormat.equals("original")) {
                format = videoUrl.substring(videoUrl.lastIndexOf(".") + 1);
            }else {
                format = exportFormat;
            }

            switch (format) {
                case "mp3" -> outputAudioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MP3;
                case "wav" -> outputAudioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE;
                case "aac" -> outputAudioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
                case "flac" -> outputAudioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_FLAC;
                default -> throw new XXException("Invalid audio audio format");
            }


            file = File.createTempFile("audio", "." + format);

            recorder = new FFmpegFrameRecorder(file, audioChannels);
        }else {
            String resolution = exportVideoDto.getResolution().toLowerCase();

            int inputHeight = grabber.getImageHeight();
            int inputWidth = grabber.getImageWidth();

            int outputHeight;
            int outputWidth;


            if(resolution.equals("original")){
                outputHeight = inputHeight;
                outputWidth = inputWidth;
            }else {

                int guideDimension;
                if(resolution.endsWith("p")){
                    guideDimension = Integer.parseInt(resolution.substring(0, resolution.length() - 1));
                }else if (resolution.endsWith("k")){
                    guideDimension = Integer.parseInt(resolution.substring(0, resolution.length() - 1)) * 1024;
                }else {
                    throw new XXException("Invalid resolution");
                }


                if (inputHeight < inputWidth) {
                    outputHeight = guideDimension;
                    outputWidth = (int) (inputWidth * (outputHeight / (double) inputHeight));
                } else {
                    outputWidth = guideDimension;
                    outputHeight = (int) (inputHeight * (outputWidth / (double) inputWidth));
                }
            }

            int outputVideoCodec;
            String exportVideoCodec = exportVideoDto.getVideoCodec().toLowerCase();

            if(exportVideoCodec.equals("original")){
                outputVideoCodec = grabber.getVideoCodec();
            }else {
                switch (exportVideoCodec){
                    case "h.264" -> outputVideoCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
                    case "hevc" -> outputVideoCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC;
                    case "av1" -> outputVideoCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1;
                    default -> throw new XXException("Invalid video codec");
                }
            }

            double frameRate;

            String exportFrameRate = exportVideoDto.getFrameRate();
            if(exportFrameRate.equals("original")){
                frameRate = grabber.getFrameRate();
            }else {
                frameRate = Double.parseDouble(exportFrameRate.substring(0, exportFrameRate.length() - 3));
            }

            if(exportFormat.equals("original")){
                format = videoUrl.substring(videoUrl.lastIndexOf(".")+1);
            }else {
                format = exportFormat;
            }

            int videoBitrate;

            String exportVideoBitrate = exportVideoDto.getVideoBitRate();

            switch (exportVideoBitrate) {
                case "original" -> videoBitrate = grabber.getVideoBitrate();
                case "lower" -> videoBitrate = (int) (grabber.getVideoBitrate() * 0.75); // Decrease by 25%
                case "higher" -> videoBitrate = (int) (grabber.getVideoBitrate() * 1.25); // Increase by 25%
                default -> throw new XXException("Invalid video bit rate");
            }

            outputAudioCodec = grabber.getAudioCodec();

            file = File.createTempFile("video", "." + format);
            recorder = new FFmpegFrameRecorder(file, outputWidth, outputHeight);
            recorder.setAudioChannels(audioChannels);
            recorder.setFrameRate(frameRate);
            recorder.setVideoBitrate(videoBitrate);
            recorder.setVideoCodec(outputVideoCodec);

        }

        recorder.setFormat(format);
        recorder.setAudioCodec(outputAudioCodec);
        recorder.setAudioBitrate(audioBitRate);
        recorder.setSampleRate(sampleRate);

        if(fast){
            recorder.setVideoOption("crf", "23");  // Adjust for quality vs speed
            recorder.setVideoOption("preset", "veryfast");  // Use a faster preset
            recorder.setVideoOption("tune", "zerolatency");
        }

        recorder.start();


        for (Long[] interval : intervals) {
            grabber.setTimestamp(interval[0] * 1000, true);
            while (grabber.getTimestamp() < interval[1] * 1000) {
                if(ThreadExecutionUtils.isThreadInterrupted()){
                    recorder.stop();
                    grabber.stop();
                    recorder.close();
                    grabber.close();
                    log.info("Job interrupted while cutting");
                    ThreadExecutionUtils.launchInterruptedException();
                }
                Frame frame;
                if(onlyAudio) {
                    frame = grabber.grabSamples();
                } else {
                    frame = grabber.grab();
                }
                recorder.record(frame);
            }
        }


        recorder.stop();
        grabber.stop();
        recorder.close();
        grabber.close();

        ThreadExecutionUtils.checkThreadExecution();

        return file;
    }

    public static File cutVideo(String videoUrl,
                                List<Long[]> intervals,
                                boolean keepInputOptions,
                                boolean fast,
                                int imageWidth,
                                int imageHeight,
                                int frameRate,
                                int audioChannels,
                                int videoCodec,
                                int audioCodec,
                                int videoBitrate,
                                int audioBitrate,
                                int sampleRate,
                                String format
    ) throws IOException {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl) ;
        grabber.start();

        String extension= videoUrl.substring(videoUrl.lastIndexOf("."));

        File file = File.createTempFile("video", extension);

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file.getAbsolutePath(),
                keepInputOptions ? grabber.getImageWidth() : imageWidth,
                keepInputOptions ? grabber.getImageHeight() : imageHeight);

        recorder.setFrameRate(keepInputOptions ? grabber.getFrameRate() : frameRate);
        recorder.setAudioChannels(keepInputOptions ? grabber.getAudioChannels() : audioChannels);
        recorder.setVideoCodec(keepInputOptions ? grabber.getVideoCodec() : videoCodec);
        recorder.setAudioCodec(keepInputOptions ? grabber.getAudioCodec() : audioCodec);
        recorder.setVideoBitrate(keepInputOptions ? grabber.getVideoBitrate() : videoBitrate);
        recorder.setAudioBitrate(keepInputOptions ? grabber.getAudioBitrate() : audioBitrate);
        recorder.setSampleRate(keepInputOptions ? grabber.getSampleRate() : sampleRate);
        recorder.setFormat(keepInputOptions ? grabber.getFormat() : format);


        if(fast){
            recorder.setVideoOption("crf", "23");  // Adjust for quality vs speed
            recorder.setVideoOption("preset", "veryfast");  // Use a faster preset
            recorder.setVideoOption("tune", "zerolatency");
        }

        recorder.start();


        for (Long[] interval : intervals) {
            grabber.setTimestamp(interval[0] * 1000, true);
            while (grabber.getTimestamp() < interval[1] * 1000) {
                Frame frame = grabber.grab();
                recorder.record(frame);
            }
        }


        recorder.stop();
        grabber.stop();
        recorder.close();
        grabber.close();
        return file;
    }


    public static ResourceDataDto getResourceData(String videoUrl) throws IOException {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoUrl);
        grabber.start();

        ResourceDataDto resourceDataDto = ResourceDataDto.builder()
                .width(grabber.getImageWidth())
                .height(grabber.getImageHeight())
                .frameRate(grabber.getFrameRate())
                .hasAudio(grabber.hasAudio())
                .durationInSeconds((double) grabber.getLengthInTime() / 1000.0)
                .hasVideo(grabber.hasVideo())
                .audioSources(grabber.hasAudio() ? 1 : 0)
                .audioChannels(grabber.getAudioChannels())
                .build();

        grabber.stop();
        grabber.close();
        return resourceDataDto;

    }


}