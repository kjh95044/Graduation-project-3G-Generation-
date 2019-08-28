package com.example.measuring;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class WatershedSegment {
        public Mat markers = new Mat();

        public void setMarkers(Mat markerImage)
        {
            markerImage.convertTo(markers, CvType.CV_8U);
        }

        public Mat process(Mat image)
        {
            Imgproc.watershed(image, markers);
            markers.convertTo(markers, CvType.CV_8U);
            return markers;
        }
}
