package com.hypersense.gpuimage;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import jp.co.cyberagent.android.gpuimage.GPUImage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.googlecode.javacv.FFmpegFrameGrabber;
import com.googlecode.javacv.FFmpegFrameRecorder;
import com.googlecode.javacv.Frame;
import com.googlecode.javacv.FrameGrabber.Exception;
import com.googlecode.javacv.cpp.avcodec.AVCodec;
import com.googlecode.javacv.cpp.avcodec.AVCodecContext;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.avcodec;
import com.googlecode.javacv.cpp.avformat;
import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_imgproc;

/**
 * Applies video effects, handles frame extraction, GPU rendering via
 * BitmapEffectManager and and re-encoding <br>
 * -Must be created in the same thread as the BitmapEffectManager<br>
 * -Has native ffmpeg handles, it requires one instance per video file<br>
 * -After processVideo finishes object must be manually released via stop()<br>
 */
public class VideoEffect {

	public ArrayList<Frame> frames = new ArrayList<Frame>();
	FFmpegFrameGrabber frameGrabber;

	public int width, height;
	public int audioChannels;
	public String outputFileName;
	private FFmpegFrameRecorder recorder;
	private boolean started = false;

	/**
	 * 
	 * @param sourceFilePath
	 *            source video file path
	 * @param outputFilePath
	 *            output video file path
	 */
	public VideoEffect(String sourceFilePath, String outputFilePath) {
		frameGrabber = new FFmpegFrameGrabber(sourceFilePath);
		this.outputFileName = outputFilePath;
	}

	/**
	 * Lazy init function, sets up ffmpeg codec backends
	 */
	public void start() {
		try {
			frameGrabber.setFormat("mp4");
			frameGrabber.start();
			height = frameGrabber.getImageHeight();
			width = frameGrabber.getImageWidth();
			audioChannels = frameGrabber.getAudioChannels();
			recorder = new FFmpegFrameRecorder(outputFileName, width, height);
			recorder.setAudioChannels(audioChannels);
			recorder.start();
			started = true;
		} catch (Exception e) {
			e.printStackTrace();
		} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Extracts frames, applies effects via the BitmapEffectManager, and
	 * re-encodes the video to outputFileName
	 * 
	 * @param bfm
	 *            the effect manager, this must have an active effect and MUST
	 *            be called on the same thread as the BitmapEffectManager
	 * @return true if the process completed, false otherwise
	 */
	public boolean processVideo(GPUImage bfm) {
		if (!started) {
			start();
		}

		while (true) {
			Frame frame = getNextFrame();
			//
			if (frame == null) {
				try {
					recorder.stop();
					recorder.release();
				} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return false;
				}
				return true;
			}

			if (frame.image != null) {
				Bitmap bitmap = Bitmap.createBitmap(width, height,
						Bitmap.Config.ARGB_8888);

				frame.image.getByteBuffer();
				bitmap.copyPixelsFromBuffer(frame.image.getByteBuffer());
				bfm.setImage(bitmap);
				
				bitmap = bfm.getBitmapWithFilterApplied();

				IplImage image = IplImage.create(bitmap.getWidth(),
						bitmap.getHeight(), opencv_core.IPL_DEPTH_8U, 4);
				bitmap.copyPixelsToBuffer(image.getByteBuffer());
				try {
					recorder.record(image);
				} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
					e.printStackTrace();
					return false;
				} finally {
					bitmap.recycle();
				}

			}
			if (frame.samples != null) {
				try {
					recorder.record(frame.samples);
				} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
					e.printStackTrace();
					return false;
				}
			}
		}
	}

	/**
	 * Extracts and converts frame image to RGBA8888
	 * 
	 * @return extracted frame with correct pixel format
	 */
	public Frame getNextFrame() {
		Frame frame = null;
		try {
			frame = frameGrabber.grabFrame();
			if (frame == null) {
				return null;
			}
			if (frame.image != null) {
				System.out.println("Channels: "
						+ frame.image.asCvMat().channels());
				System.out.println("Depth: " + frame.image.asCvMat().depth());

				/**
				 * Convert to reasonable pixel format
				 */
				opencv_imgproc.cvCvtColor(frame.image, frame.image,
						opencv_imgproc.CV_BGR2RGB);

				IplImage rgbaImage = IplImage.create(width, height,
						IPL_DEPTH_8U, 4);

				opencv_imgproc.cvCvtColor(frame.image, rgbaImage,
						opencv_imgproc.CV_RGB2RGBA);

				frame.image = rgbaImage;
				return frame;
			} else {
				return frame;
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				frameGrabber.stop();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			return null;
		}
	}

	public void stop() throws com.googlecode.javacv.FrameRecorder.Exception {
		try {
			frameGrabber.stop();
			recorder.stop();
			recorder.release();
			frameGrabber.release();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}