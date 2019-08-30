package com.example.measuring;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class WatershedSegment {
        public Mat markers = new Mat();

        public void setMarkers(Mat markerImage)
        {
            markerImage.convertTo(markers, CvType.CV_32S);
        }

        public Mat process(Mat image)
        {
            Imgproc.watershed(image, markers);
            return this.markers;
        }


        public Mat getSegmentation() {
            Mat temp = new Mat();
            markers.convertTo(temp, CvType.CV_8U);

            return temp;
        }

        public Mat getWatershed() {
            Mat temp = new Mat();
            markers.convertTo(temp, CvType.CV_8U, 255, 255);

            return temp;
        }
}
