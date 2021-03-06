package edu.ucsb.cs.cs184.hjiang00.musicperformer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

/**
 * Created by Jiang on 5/31/2018.
 */

public class GestureRecognition {
    public List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
    public List<MatOfPoint> hullP = new ArrayList<MatOfPoint>();
    public Map<Double, Point> fingersOrder = new TreeMap<Double, Point>();
    public Map<Double, Integer> defectPointsOrdered = new TreeMap<Double, Integer>();
    public ArrayList<Integer> defectIndex = new ArrayList<Integer>();
    public List<Point> defectPoints = new ArrayList<Point>();
    public List<Point> fingers = new ArrayList<Point>();
    public List<Double> features = new ArrayList<Double>();

    public Rect boundingRect;
    public Point palm = new Point();

    private boolean isHand = false;
    public double palmRadius;
    public int contourIndex = -1;

    public Mat hierarchy = new Mat();
    public MatOfInt hull = new MatOfInt();
    public MatOfPoint2f defectMat = new MatOfPoint2f();
    public MatOfInt4 defectsMat = new MatOfInt4();
    public MatOfPoint2f approxContour = new MatOfPoint2f();

    //Native code get radius
    public native double findInscribedCircleJNI(long imgAddr, double rectTLX, double rectTLY,
                                                double rectBRX, double rectBRY, double[] incircleX, double[] incircleY, long contourAddr);

    void findInscribedCircle(Mat img)
    {
        double[] cirx = new double[]{0};
        double[] ciry = new double[]{0};

        Point tl = boundingRect.tl();
        Point br = boundingRect.br();

        palmRadius = findInscribedCircleJNI(img.getNativeObjAddr(), tl.x, tl.y, br.x, br.y, cirx, ciry, approxContour.getNativeObjAddr());
        palm.x = cirx[0];
        palm.y = ciry[0];

    }

    boolean detectIsHand(Mat img)
    {
        int centerX = 0;
        int centerY = 0;
        if (boundingRect != null) {
            centerX = boundingRect.x + boundingRect.width/2;
            centerY = boundingRect.y + boundingRect.height/2;
        }
        if (contourIndex == -1)
            isHand = false;
        else if (boundingRect == null) {
            isHand = false;
        } else if ((boundingRect.height == 0) || (boundingRect.width == 0))
            isHand = false;
        else if ((centerX < img.cols()/4) || (centerX > img.cols()*3/4))
            isHand = false;
        else
            isHand = true;
        return isHand;
    }

    //Get the feature by label
    String feature2SVMString(int label)
    {
        String ret = Integer.toString(label) + " ";
        int i;
        for (i = 0; i < features.size(); i++)
        {
            int id = i + 1;
            ret = ret + id + ":" + features.get(i) + " ";
        }
        ret = ret + "\n";
        return ret;
    }

    //Extract hand from image
    String handExtraction(Mat img, int label)
    {
        String ret = null;
        if ((detectIsHand(img))) {
            defectMat.fromList(defectPoints);

            List<Integer> dList = defectsMat.toList();
            Point[] contourPts = contours.get(contourIndex).toArray();
            Point prevDefectVec = null;
            int i;
            for (i = 0; i < defectIndex.size(); i++)
            {
                int curDlistId = defectIndex.get(i);
                int curId = dList.get(curDlistId);

                Point curDefectPoint = contourPts[curId];
                Point curDefectVec = new Point();
                curDefectVec.x = curDefectPoint.x - palm.x;
                curDefectVec.y = curDefectPoint.y - palm.y;

                if (prevDefectVec != null) {
                    double dotProduct = curDefectVec.x*prevDefectVec.x +
                            curDefectVec.y*prevDefectVec.y;
                    double crossProduct = curDefectVec.x*prevDefectVec.y -
                            prevDefectVec.x*curDefectVec.y;

                    if (crossProduct <= 0)
                        break;
                }


                prevDefectVec = curDefectVec;

            }

            int startId = i;
            int countId = 0;

            ArrayList<Point> finTipsTemp = new ArrayList<Point>();

            if (defectIndex.size() > 0) {
                boolean end = false;

                for (int j = startId; ; j++)
                {
                    if (j == defectIndex.size())
                    {

                        if (end == false) {
                            j = 0;
                            end = true;
                        }
                        else
                            break;
                    }



                    if ((j == startId) && (end == true))
                        break;

                    int curDlistId = defectIndex.get(j);
                    int curId = dList.get(curDlistId);

                    Point curDefectPoint = contourPts[curId];
                    Point fin0 = contourPts[dList.get(curDlistId-2)];
                    Point fin1 = contourPts[dList.get(curDlistId-1)];
                    finTipsTemp.add(fin0);
                    finTipsTemp.add(fin1);

                    Core.circle(img, curDefectPoint, 2, new Scalar(0, 0, 255), -5);

                    countId++;
                }

            }

            int count = 0;
            features.clear();
            for (int fid = 0; fid < finTipsTemp.size(); )
            {
                if (count > 5)
                    break;

                Point curFinPoint = finTipsTemp.get(fid);

                if ((fid%2 == 0)) {

                    if (fid != 0) {
                        Point prevFinPoint = finTipsTemp.get(fid-1);
                        curFinPoint.x = (curFinPoint.x + prevFinPoint.x)/2;
                        curFinPoint.y = (curFinPoint.y + prevFinPoint.y)/2;
                    }


                    if (fid == (finTipsTemp.size() - 2) )
                        fid++;
                    else
                        fid += 2;
                } else
                    fid++;


                Point disFinger = new Point(curFinPoint.x-palm.x, curFinPoint.y-palm.y);
                Double f1 = (disFinger.x)/palmRadius;
                Double f2 = (disFinger.y)/palmRadius;
                features.add(f1);
                features.add(f2);
                count++;

            }

            ret = feature2SVMString(label);


        }

        return ret;
    }

    void findBiggestContour()
    {
        int idx = -1;
        int cNum = 0;

        for (int i = 0; i < contours.size(); i++)
        {
            int curNum = contours.get(i).toList().size();
            if (curNum > cNum) {
                idx = i;
                cNum = curNum;
            }
        }

        contourIndex = idx;
    }




}
