//package com.walkgis.tiles.util;
//
//import ch.qos.logback.core.util.FileUtil;
//import com.walkgis.tiles.MainApp;
//import com.walkgis.tiles.web.MainViewController;
//import mil.nga.geopackage.BoundingBox;
//import org.apache.commons.cli.*;
//import org.beetl.core.Configuration;
//import org.beetl.core.GroupTemplate;
//import org.beetl.core.Template;
//import org.beetl.core.resource.ClasspathResourceLoader;
//import org.beetl.core.resource.StringTemplateResourceLoader;
//import org.gdal.gdal.Band;
//import org.gdal.gdal.Dataset;
//import org.gdal.gdal.Driver;
//import org.gdal.gdal.gdal;
//import org.gdal.gdalconst.gdalconst;
//import org.gdal.osr.SpatialReference;
//import org.locationtech.jts.geom.Envelope;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.util.FileCopyUtils;
//import org.springframework.util.StringUtils;
//
//import java.io.*;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.sql.SQLException;
//import java.util.*;
//import java.util.concurrent.*;
//
///**
// * Created by JerFer
// * Date: 2017/12/13.
// * 多线程版本
// */
//public class GDAL2TilesMulti {
//    private static final Logger logger = LoggerFactory.getLogger(GDAL2TilesMulti.class);
//
//    private static PDFReader reader = new PDFReader();
//    private boolean stopped = false;
//    private int tilesize = 256;
//    private String tileext = "png";
//    private boolean scaledquery = true;
//    private int querysize = 4 * tilesize;
//    private EnumResampling resampling = EnumResampling.GRA_Average;
//    private int tminz = -1, tmaxz = -1;
//    private double ominx, ominy, omaxx, omaxy;
//    private double[] swne;
//    private List<int[]> tminmax;
//    private double[] out_gt;
//    private int nativezoom = -1;
//    private double[] tsize;
//    //    private Map<String, String> options;
//    private String input;
//    private String output_folder;
//    private Dataset in_ds, out_ds;
//    private Band alphaband;
//    private SpatialReference out_srs;
//    private EnumProfile profile;
//    private Boolean resume = false;
//    private Driver out_drv, mem_drv;
//    private GlobalMercator mercator;
//    private GlobalGeodetic geodetic;
//    private int dataBandsCount;
//    private String in_srs_wkt = "";
//    private boolean kml;
//
//    //    private String[] args;
//    private Options parser;
//    private int[] tileswne;
//    private List<Double> in_nodata;
//    private String tiledriver = "";
//    private boolean overviewquery = false;
//    public static boolean geopackage = false;
//    private int dataType = 1;
//    private int byteToType = 0;
//    private GeopackageUtil geopackageUtil;
//    private String s_srs;
//    private File tmp_vrt_filename = null;
//    private MainViewController mainViewController;
//
//    private final ExecutorService service = Executors.newFixedThreadPool(8);
//
//    //-l -p raster -z 0-5 -w none <image> <tilesdir>
//    public GDAL2TilesMulti(MainViewController mainViewController, String input_path, String output_folder, EnumResampling resampling, EnumProfile profile) {
//        this.mainViewController = mainViewController;
//        this.tilesize = 256;
//        this.tiledriver = "PNG";
//        this.tileext = "png";
//
//        this.scaledquery = true;
//        this.querysize = 4 * this.tilesize;
//        this.overviewquery = false;
//
//        this.out_drv = null;
//        this.mem_drv = null;
//        this.in_ds = null;
//        this.out_ds = null;
//        this.out_srs = null;
//        this.nativezoom = 0;
//        this.tminmax = null;
//        this.tsize = null;
//        this.alphaband = null;
//        this.dataBandsCount = 0;
//        this.out_gt = null;
////        this.tileswne = null;
//        this.swne = null;
//        this.ominx = 0;
//        this.omaxx = 0;
//        this.omaxy = 0;
//        this.ominy = 0;
//
//        this.stopped = false;
//        this.input = input_path;
//        this.output_folder = output_folder;
//        this.profile = profile;
//
////        optparse_init();
//
////        this.options = parse_options(args);
////        this.args = parse_args(args);
//
//        setResampling(resampling);
//        this.tminz = -1;
//        this.tmaxz = -1;
////
////        if (this.options.containsKey("zoom")) {
////            String[] minmax = this.options.get("zoom").split("-");
////            this.tminz = Integer.parseInt(minmax[0]);
////            if (minmax[1] != null) {
////                this.tmaxz = Integer.parseInt(minmax[1]);
////            } else this.tmaxz = this.tminz;
////        }
//
//        this.kml = false;
//    }
//
//    public void process(GeopackageUtil geopackageUtil) throws Exception {
//        // Opening and preprocessing of the input file
//        if (StringUtils.isEmpty(this.input))
//            throw new Exception("No input file specified");
//        if (StringUtils.isEmpty(this.output_folder))
//            throw new Exception("No output_folder file specified");
//
//        this.geopackageUtil = geopackageUtil;
//        open_input();
//
//        // Generation of main metadata files and HTML viewers
//        generate_metadata();
//
//        // Generation of the lowest tiles
//        List<TileDetail> tileDetails = new ArrayList<>();
//
//        TileJobInfo tileJobInfo = generate_base_tiles(tileDetails);
//
//        for (int i = 0; i < tileDetails.size(); i++) {
//            service.execute(new BaseTileTask(tileJobInfo, tileDetails.get(i)));
//        }
//
//        service.shutdown();
//        try {
//            service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } finally {
//            create_overview_tiles(tileJobInfo, output_folder, null);
//        }
//    }
//
//    private void create_overview_tiles(TileJobInfo tileJobInfo, String outputFolder, Map<String, Object> options) {
//        Driver memDriver = gdal.GetDriverByName("MEM");
//        String tileDriver = tileJobInfo.tileDriver;
//        Driver outDriver = gdal.GetDriverByName(tileDriver);
//
//        int tilebands = tileJobInfo.nbDataBands + 1;
//
//        int tcount = 0;
//        for (int tz = tileJobInfo.tmaxz - 1; tz > tileJobInfo.tminz - 1; tz--) {
//            int[] tminxytmaxxy = tileJobInfo.tminmax.get(tz);
//            tcount += (1 + Math.abs(tminxytmaxxy[2] - tminxytmaxxy[0])) * (1 + Math.abs(tminxytmaxxy[3] - tminxytmaxxy[1]));
//        }
//
//        int ti = 0;
//
//        if (tcount == 0) return;
//
//        logger.debug("Generating Overview Tiles:");
//        //这里又开始进度条
//
//        for (int tz = tileJobInfo.tmaxz - 1; tz > tileJobInfo.tminz - 1; tz--) {
//            int[] tminxytmaxxy = tileJobInfo.tminmax.get(tz);
//            for (int ty = tminxytmaxxy[3]; ty > tminxytmaxxy[1] - 1; ty--) {
//                for (int tx = tminxytmaxxy[0]; tx < tminxytmaxxy[2] + 1; tx++) {
//                    ti += 1;
//                    String tilefilename = tileJobInfo.outputFilePath + File.separator + tz + File.separator + String.format("%s_%s.%s", tx, ty, tileJobInfo.tileExtension);
//
//                    logger.debug(tilefilename);
//
//                    if (new File(tilefilename).exists()) {
//                        progressbar(ti / (float) tcount);
//                        continue;
//                    }
//
//                    FileUtil.createMissingParentDirectories(new File(tilefilename));
//
//                    Dataset dsquery = memDriver.Create("", 2 * tileJobInfo.tileSize, 2 * tileJobInfo.tileSize, tilebands);
//                    Dataset dstile = memDriver.Create("", tileJobInfo.tileSize, tileJobInfo.tileSize, tilebands);
//
//                    for (int y = 2 * ty; y < 2 * ty + 2; y++) {
//                        for (int x = 2 * tx; x < 2 * tx + 2; x++) {
//                            int[] minxytmaxxy = tileJobInfo.tminmax.get(tz + 1);
//                            if (x >= minxytmaxxy[0] && x <= minxytmaxxy[2] &&
//                                    y >= minxytmaxxy[1] && y <= minxytmaxxy[3]) {
//                                File file = new File(output_folder + File.separator + ((tz + 1)) + File.separator + String.format("%s_%s.%s", x, y, tileJobInfo.tileExtension));
//                                if (!file.exists())
//                                    logger.debug(file.getAbsolutePath());
//
//                                Dataset dsquerytile = gdal.Open(file.getAbsolutePath(), gdalconst.GA_ReadOnly);
//                                if (dsquerytile == null)
//                                    continue;
//                                int tileposy, tileposx;
//                                if ((ty == 0 && y == 1) ||
//                                        (ty != 0 && (y % (2 * ty)) != 0)) {
//                                    tileposy = 0;
//                                } else {
//                                    tileposy = tileJobInfo.tileSize;
//                                }
//
//                                if (tx > 0)
//                                    tileposx = x % (2 * tx) * tileJobInfo.tileSize;
//                                else if (tx == 0 && x == 1) {
//                                    tileposx = tileJobInfo.tileSize;
//                                } else {
//                                    tileposx = 0;
//                                }
//
//                                byte[] temp = new byte[1024 * 1024 * 4];
//                                dsquerytile.ReadRaster(0, 0, tileJobInfo.tileSize, tileJobInfo.tileSize, tileJobInfo.tileSize, tileJobInfo.tileSize, dataType, temp,
//                                        new int[]{1, 2, 3, 4});
//                                dsquery.WriteRaster(tileposx, tileposy, tileJobInfo.tileSize, tileJobInfo.tileSize, tileJobInfo.tileSize, tileJobInfo.tileSize, dataType, temp,
//                                        reader.getBandList(dsquery.getRasterCount()));
//                            }
//                        }
//                    }
//
//                    scale_query_to_tile(dsquery, dstile, tilefilename);
//
//                    //antialias
//                    if (this.resampling != EnumResampling.Other) {
//                        this.out_drv.CreateCopy(tilefilename, dstile, 0);
//                    }
//
////                    if (!Boolean.parseBoolean(this.options.get("verbose")) && !Boolean.parseBoolean(this.options.get("quiet"))) {
////                        this.progressbar(ti / (double) tcount);
////                    }
//                }
//            }
//        }
//        if (geopackage)
//            geopackageUtil.close();
//    }
//
//    private void create_base_tile(TileJobInfo tileJobInfo, TileDetail tileDetail) {
//        int dataBandsCount = tileJobInfo.nbDataBands;
//        String output = tileJobInfo.outputFilePath;
//        String tileext = tileJobInfo.tileExtension;
//        Integer tileSize = tileJobInfo.tileSize;
//        OptionObj options = tileJobInfo.options;
//
//        int tileBands = dataBandsCount + 1;
//        Dataset ds;
//
////        Dataset cachedDs = threadLocal.get();
//
////        if (cachedDs != null)
////            ds = cachedDs;
////        else {
//        ds = gdal.Open(tileJobInfo.srcFile, gdalconst.GA_ReadOnly);
////            threadLocal.set(ds);
////        }
//
//        Driver mem_drv = gdal.GetDriverByName("MEM");
//        Driver out_drv = gdal.GetDriverByName(tileJobInfo.tileDriver);
//        Band alphaband = ds.GetRasterBand(1).GetMaskBand();
//
//
//        int tx = tileDetail.tx;
//        int ty = tileDetail.ty;
//        int tz = tileDetail.tz;
//        int rx = tileDetail.rx;
//        int ry = tileDetail.ry;
//        int rxsize = tileDetail.rxsize;
//        int rysize = tileDetail.rysize;
//        int wx = tileDetail.wx;
//        int wy = tileDetail.wy;
//        int wxsize = tileDetail.wxsize;
//        int wysize = tileDetail.wysize;
//        querysize = tileDetail.querysize;
//
//
//        String tilefilename = output + File.separator + tz + File.separator + String.format("%s_%s.%s", tx, ty, tileext);
//        new File(tilefilename).getParentFile().mkdirs();
//        Dataset dstile = mem_drv.Create("", tileSize, tileSize, tileBands);
//
//        byte[] data = new byte[1024 * 1024 * ds.GetRasterCount()];
//        byte[] alpha = new byte[1024 * 1024];
//        if (rxsize != 0 && rysize != 0 && wxsize != 0 && wysize != 0) {
//            ds.ReadRaster(rx, ry, rxsize, rysize, wxsize, wysize, dataType, data, reader.getBandList(ds.GetRasterCount()));
//            alphaband.ReadRaster(rx, ry, rxsize, rysize, wxsize, wysize, dataType, alpha);
//
//            logger.info(String.format("rx,ry,rxsize,rysize,wxsize,wysize=%d,%d,%d,%d,%d,%d", rx, ry, rxsize, rysize, wxsize, wysize));
//        } else return;
//
//        if (this.tilesize == querysize) {
//            dstile.WriteRaster(wx, wy, wxsize, wysize, wxsize, wysize, dataType, data, reader.getBandList(ds.getRasterCount()));
//            dstile.WriteRaster(wx, wy, wxsize, wysize, wxsize, wysize, dataType, alpha, new int[]{4});
//        } else {
//            Dataset dsquery = this.mem_drv.Create("", querysize, querysize, tileBands);
//            dsquery.WriteRaster(wx, wy, wxsize, wysize, wxsize, wysize, dataType, data, reader.getBandList(ds.getRasterCount()));
//            dsquery.WriteRaster(wx, wy, wxsize, wysize, wxsize, wysize, dataType, alpha, new int[]{4});
//
//            scale_query_to_tile(dsquery, dstile, tilefilename);
//
//            dsquery.delete();
//        }
//        data = null;
//        alpha = null;
//        //antialias
//        if (this.resampling != EnumResampling.Other) {
//            try {
//                this.out_drv.CreateCopy(tilefilename, dstile, 0);
//                if (geopackage)
//                    geopackageUtil.insertTile(new File(tilefilename), tz, tx, ty);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//    }
//
//    public void error(String msg, String details) {
//        if (details != null) {
//            logger.error(msg + "\n\n" + details);
//        } else
//            logger.error(msg);
//    }
//
//    public void progressbar(double complete) {
//        mainViewController.updateProgress(complete, "", "");
//    }
//
//    public void stop() {
//        this.stopped = true;
//    }
//
//    private String gettempfilename(String suffix) {
//        String tmpdir = System.getProperty("java.io.tmpdir");
//        int d = 0 + (int) (Math.random() * (1000000000 - 0 + 1));
//        String random_part = String.format("file%f", d);
//        return tmpdir + File.separator + random_part + suffix;
//    }
//
//    private void open_input() throws Exception {
//        this.out_drv = gdal.GetDriverByName(this.tiledriver);
//        this.mem_drv = gdal.GetDriverByName("MEM");
//
//        if (new File(this.input).exists()) {
//            reader.init(this.input);
//            this.in_ds = reader.getDataset();
//        }
//
//        //初始化in_nodata
//        this.in_nodata = new ArrayList<>();
//        for (int i = 1, size = this.in_ds.GetRasterCount() + 1; i < size; i++) {
//            Double[] noData = new Double[1];
//            this.in_ds.GetRasterBand(i).GetNoDataValue(noData);
//            if (noData != null) {
//                this.in_nodata.add(noData[0]);
//            }
//        }
//
//
//        SpatialReference in_srs = null;
//        if (!StringUtils.isEmpty(this.s_srs)) {
//            in_srs = new SpatialReference();
//            in_srs.SetFromUserInput(this.s_srs);
//            this.in_srs_wkt = in_srs.ExportToWkt();
//        } else {
//            this.in_srs_wkt = this.in_ds.GetProjection();
//            if (StringUtils.isEmpty(this.in_srs_wkt) && this.in_ds.GetGCPCount() != 0) {
//                this.in_srs_wkt = this.in_ds.GetGCPProjection();
//            }
//            if (!StringUtils.isEmpty(this.in_srs_wkt)) {
//                in_srs = new SpatialReference();
//                in_srs.ImportFromWkt(this.in_srs_wkt);
//            }
//        }
//
//        this.out_srs = new SpatialReference();
//
//        if (this.profile == null) {
//            String s = in_srs.GetAuthorityCode(null);
//            if (s.equals("4326")) this.profile = EnumProfile.geodetic;
//            else if (s.equals("900913")) this.profile = EnumProfile.mercator;
//            else this.profile = EnumProfile.geodetic;
//        }
//
//        if (this.profile == EnumProfile.mercator)
//            this.out_srs.ImportFromEPSG(900913);
//        else if (this.profile == EnumProfile.geodetic)
//            this.out_srs.ImportFromEPSG(4326);
//        else this.out_srs = in_srs;
//
//        this.out_ds = null;
//
//        if (this.profile == EnumProfile.geodetic || this.profile == EnumProfile.mercator) {
//            double[] trans = this.in_ds.GetGeoTransform();
//            if (trans[0] == 0.0 && trans[1] == 1.0 && trans[2] == 0.0 && trans[3] == 0.0 &&
//                    trans[4] == 0.0 && trans[5] == 1.0) {
//                throw new Exception("There is no georeference - neither affine transformation (worldfile) nor GCPs. You can generate only 'raster' profile tiles.\n");
//            }
//            if (in_srs != null) {
//                if (in_srs.ExportToProj4() != this.out_srs.ExportToProj4() || this.in_ds.GetGCPCount() != 0) {
//                    this.out_ds = gdal.AutoCreateWarpedVRT(this.in_ds, in_srs_wkt, this.out_srs.ExportToWkt());
//                }
//            }
//            if (this.in_nodata != null && this.in_nodata.size() > 0) {
//                this.tmp_vrt_filename = File.createTempFile("-gdal2tiles", ".vrt");
//                this.out_ds.GetDriver().CreateCopy(this.tmp_vrt_filename.getAbsolutePath(), this.out_ds);
//
//                String s = FileCopyUtils.copyToString(new FileReader(this.tmp_vrt_filename));
//                s = s.replace("<GDALWarpOptions>", "<GDALWarpOptions><Option name=\"INIT_DEST\">NO_DATA</Option><Option name=\"UNIFIED_SRC_NODATA\">YES</Option>");
//                for (int i = 0; i < this.in_nodata.size(); i++) {
//                    s = s.replace(
//                            String.format("<BandMapping src=\"%d\" dst=\"%d\"/>", i + 1, i + 1),
//                            String.format("<BandMapping src=\"%d\" dst=\"%d\"><SrcNoDataReal>%f</SrcNoDataReal><SrcNoDataImag>0</SrcNoDataImag><DstNoDataReal>%f</DstNoDataReal><DstNoDataImag>0</DstNoDataImag></BandMapping>", i + 1, i + 1, this.in_nodata.get(i), this.in_nodata.get(i))
//                    );
//                }
//
//                FileCopyUtils.copy(s, new FileWriter(this.tmp_vrt_filename));
//                this.out_ds = gdal.Open(this.tmp_vrt_filename.getAbsolutePath(), gdalconst.GA_ReadOnly);
//                this.out_ds.SetMetadataItem("NODATA_VALUES", String.format("%f %f %f", this.in_nodata.get(0), this.in_nodata.get(1), this.in_nodata.get(2)));
//            }
//
//            if ((this.in_nodata == null || this.in_nodata.size() == 0) && this.out_ds.GetRasterCount() == 3) {
//                File tempfilename = File.createTempFile("-gdal2tiles", ".vrt");
//                this.out_ds.GetDriver().CreateCopy(tempfilename.getAbsolutePath(), this.out_ds);
//
//                String s = FileCopyUtils.copyToString(new FileReader(tempfilename));
//                s = s.replace("<BlockXSize>", String.format("<VRTRasterBand dataType=\"Byte\" band=\"%d\" subClass=\"VRTWarpedRasterBand\"><ColorInterp>Alpha</ColorInterp></VRTRasterBand><BlockXSize>", (this.out_ds.GetRasterCount() + 1)));
//                s = s.replace("</GDALWarpOptions>", String.format("<DstAlphaBand>%d</DstAlphaBand></GDALWarpOptions>", this.out_ds.GetRasterCount() + 1));
//                s = s.replace("</WorkingDataType>", "</WorkingDataType><Option name=\"INIT_DEST\">0</Option>");
//
//                FileCopyUtils.copy(s, new FileWriter(tempfilename));
//                this.out_ds = gdal.Open(tempfilename.getAbsolutePath(), gdalconst.GA_ReadOnly);
//            }
//        }
//
//        if (this.out_ds == null) {
//            this.out_ds = this.in_ds;
//        }
//
//        // 首先获取影像数据类型
//        this.dataType = this.in_ds.GetRasterBand(1).GetRasterDataType();
//        // 计算该类型的字节数
//        int typeSize = gdal.GetDataTypeSize(dataType);
//        // 一个byte8位，计算倍数只需除以8即可
//        this.byteToType = typeSize / 8;
//
//
//        this.alphaband = this.out_ds.GetRasterBand(1).GetMaskBand();
//        if ((this.alphaband.GetMaskFlags() & gdalconst.GMF_ALPHA) > 0 ||
//                this.out_ds.GetRasterCount() == 4 ||
//                this.out_ds.GetRasterCount() == 2
//        )
//            this.dataBandsCount = this.out_ds.GetRasterCount() - 1;
//        else
//            this.dataBandsCount = this.out_ds.GetRasterCount();
//
//        //Read the georeference
//        this.out_gt = this.out_ds.GetGeoTransform();
//
//        if (this.out_gt[2] != 0 && this.out_gt[4] != 0)
//            logger.error("Georeference of the raster contains rotation or skew. Such raster is not supported. Please use gdalwarp first.");
//
//        this.ominx = out_gt[0];
//        this.omaxx = out_gt[0] + this.out_ds.getRasterXSize() * this.out_gt[1];
//        this.omaxy = out_gt[3];
//        this.ominy = out_gt[3] - this.out_ds.getRasterYSize() * this.out_gt[1];
//
//        if (this.profile == EnumProfile.mercator) {
//            this.mercator = new GlobalMercator(256);
////            this.tileswne = this.mercator.tileLatLonBounds();
//            this.tminmax = new LinkedList<>();
//            for (int tz = 0; tz < 32; tz++) {
//                int[] tminxy = this.mercator.metersToTile(this.ominx, this.ominy, tz);
//                int[] tmaxxy = this.mercator.metersToTile(this.omaxx, this.omaxy, tz);
//
//                tminxy = new int[]{Math.max(0, tminxy[0]), Math.max(0, tminxy[1])};
//                tmaxxy = new int[]{(int) Math.min(Math.pow(2, tz) - 1, tmaxxy[0]), (int) Math.min(Math.pow(2, tz) - 1, tmaxxy[1])};
//
//                this.tminmax.add(tz, new int[]{tminxy[0], tminxy[1], tmaxxy[0], tmaxxy[1]});
//            }
//
//            if (this.tminz == -1) {
//                this.tminz = this.mercator.zoomForPixelSize(this.out_gt[1] * Math.max(this.out_ds.getRasterXSize(), this.out_ds.getRasterYSize()) / (float) (this.tilesize));
//            }
//            if (this.tmaxz == -1) {
//                this.tmaxz = this.mercator.zoomForPixelSize(this.out_gt[1]);
//            }
//        } else if (this.profile == EnumProfile.geodetic) {
//            this.geodetic = new GlobalGeodetic(null, 256);
////            this.tileswne = this.geodetic.tileLatLonBounds();
//            this.tminmax = new LinkedList<>();
//            for (int tz = 0; tz < 32; tz++) {
//                int[] tminxy = this.geodetic.lonlatToTile(this.ominx, this.ominy, tz);
//                int[] tmaxxy = this.geodetic.lonlatToTile(this.omaxx, this.omaxy, tz);
//
//                tminxy = new int[]{Math.max(0, tminxy[0]), Math.max(0, tminxy[1])};
//                tmaxxy = new int[]{Math.min((int) Math.pow(2, tz + 1) - 1, tmaxxy[0]),
//                        (int) Math.min(Math.pow(2, tz) - 1, tmaxxy[1])};
//
//                this.tminmax.add(tz, new int[]{tminxy[0], tminxy[1], tmaxxy[0], tmaxxy[1]});
//            }
//
//            if (this.tminz == -1) {
//                this.tminz = this.geodetic.zoomForPixelSize(this.out_gt[1] *
//                        Math.max(this.out_ds.getRasterXSize(), this.out_ds.getRasterYSize()) / (float) (this.tilesize));
//            }
//            if (this.tmaxz == -1) {
//                this.tmaxz = this.geodetic.zoomForPixelSize(this.out_gt[1]);
//            }
//        } else if (this.profile == EnumProfile.raster) {
//            this.nativezoom = (int) (Math.max(Math.ceil(log2(this.out_ds.getRasterXSize() / (float) (this.tilesize))),
//                    Math.ceil(log2(this.out_ds.getRasterYSize() / (float) (this.tilesize)))));
//
//            if (this.tminz == -1) {
//                this.tminz = 0;
//            }
//            if (this.tmaxz == -1) {
//                this.tmaxz = this.nativezoom;
//            }
//            this.tminmax = new LinkedList<>();
//            this.tsize = new double[this.tmaxz + 1];
//
//            for (int tz = 0; tz < this.tmaxz + 1; tz++) {
//                double tsize = Math.pow(2.0, this.nativezoom - tz) * this.tilesize;
//
//                int[] tminxy = new int[]{0, 0};
//                int[] tmaxxy = new int[]{
//                        ((int) (Math.ceil(this.out_ds.getRasterXSize() / tsize))) - 1,
//                        ((int) (Math.ceil(this.out_ds.getRasterYSize() / tsize))) - 1
//                };
//
//                this.tsize[tz] = Math.ceil(tsize);
//                this.tminmax.add(tz, new int[]{tminxy[0], tminxy[1], tmaxxy[0], tmaxxy[1]});
//            }
////            this.tileswne = new int[]{0, 0, 0, 0};
//        }
//
//        try {
//            if (geopackage)
//                geopackageUtil.createTileTable("home", getBoundBox(), this.tminz, this.tmaxz, this.tileext);
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void generate_metadata() {
//        if (!new File(this.output_folder).exists())
//            new File(this.output_folder).mkdirs();
//        double[] southWest = new double[2];
//        double[] northEast = new double[2];
//        if (this.profile == EnumProfile.mercator) {
//            southWest = this.mercator.metersToLatLon(this.ominx, this.ominy);
//            northEast = this.mercator.metersToLatLon(this.omaxx, this.omaxy);
//
//            southWest = new double[]{Math.max(-85.05112878, southWest[0]), Math.max(-180.0, southWest[1])};
//            northEast = new double[]{Math.min(85.05112878, northEast[0]), Math.min(180.0, northEast[1])};
//
//            this.swne = new double[]{southWest[0], southWest[1], northEast[0], northEast[1]};
//
//            this.generate_openlayers();
//            this.generate_leaflet();
//        } else if (this.profile == EnumProfile.geodetic) {
//            southWest = new double[]{this.ominy, this.ominx};
//            northEast = new double[]{this.omaxy, this.omaxx};
//
//            southWest = new double[]{Math.max(-90.0, southWest[0]), Math.max(-180.0, southWest[1])};
//            northEast = new double[]{Math.min(90.0, northEast[0]), Math.min(180.0, northEast[1])};
//
//            this.swne = new double[]{southWest[0], southWest[1], northEast[0], northEast[1]};
//            this.generate_openlayers();
//        } else if (this.profile == EnumProfile.raster) {
//
//            southWest = new double[]{this.ominy, this.ominx};
//            northEast = new double[]{this.omaxy, this.omaxx};
//
//            this.swne = new double[]{southWest[0], southWest[1], northEast[0], northEast[1]};
//            this.generate_openlayers();
//        }
//        // Generate tilemapresource.xml.
//
//        if (this.resume && !new File(this.output_folder + File.separator + "tilemapresource.xml").exists()) {
//            File file = new File(this.output_folder + File.separator + "tilemapresource.xml");
//            FileOutputStream fileOutputStream = null;
//            try {
//                fileOutputStream = new FileOutputStream(file);
////                fileOutputStream.write(generate_tilemapresource().getBytes());
//                fileOutputStream.close();
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
//
//    }
//
//    private void generate_leaflet() {
//        Map<String, Object> args = new HashMap<>();
//        args.put("title", "");
//        args.put("htmltitle", "");
//        args.put("bingkey", "this.options.bingkey");
//        args.put("south", this.swne[0]);
//        args.put("west", this.swne[1]);
//        args.put("north", this.swne[2]);
//        args.put("east", this.swne[3]);
//
//        args.put("centerlon", (this.swne[2] + this.swne[0]) / 2);
//        args.put("centerlat", (this.swne[1] + this.swne[3]) / 2);
//
//        args.put("minzoom", this.tminz);
//        args.put("maxzoom", this.tmaxz);
//        args.put("beginzoom", this.tmaxz);
//
//        args.put("tile_size", this.tilesize);
//        args.put("tileformat", this.tileext);
//        args.put("publishurl", "this.options.url");
//        args.put("copyright", "this.options.copyright");
//        args.put("profile", this.profile);
//
//        String s = "";
//        try {
//            InputStream inputStream = MainApp.class.getClassLoader().getResourceAsStream("leaflet.html");
//
//            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
//            StringBuffer content = new StringBuffer();
//            while ((s = br.readLine()) != null) {
//                content = content.append(s);
//            }
//
//            //初始化代码
//            StringTemplateResourceLoader resourceLoader = new StringTemplateResourceLoader();
//            Configuration cfg = Configuration.defaultConfiguration();
//            GroupTemplate gt = new GroupTemplate(resourceLoader, cfg);
//            //获取模板
//            Template t = gt.getTemplate(content.toString());
//            t.binding(args);
//            //渲染结果
//            String str = t.render();
//
//            //保存文本到文件
//            Path rootLocation = Paths.get(this.output_folder);
//            if (Files.notExists(rootLocation)) {
//                Files.createDirectories(rootLocation);
//            }
//            //data.js是文件
//            Path path = rootLocation.resolve("leaflet.html");
//            byte[] strToBytes = str.getBytes();
//            Files.write(path, strToBytes);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    private void generate_openlayers() {
//        Map<String, Object> args = new HashMap<>();
//        args.put("title", "");
//        args.put("bingkey", "this.options.bingkey");
//        args.put("south", this.swne[0]);
//        args.put("west", this.swne[1]);
//        args.put("north", this.swne[2]);
//        args.put("east", this.swne[3]);
//
//        args.put("minzoom", this.tminz);
//        args.put("maxzoom", this.tmaxz);
//        args.put("tile_size", this.tilesize);
//        args.put("tileformat", this.tileext);
//        args.put("publishurl", "this.options.url");
//        args.put("copyright", "this.options.copyright");
//
//        args.put("tmsoffset", "-1");
//        if (this.profile == EnumProfile.raster) {
//            args.put("rasterzoomlevels", this.tmaxz + 1);
//            args.put("rastermaxresolution", Math.pow(2, this.nativezoom) * this.out_gt[1]);
//        }
//        args.put("profile", this.profile);
//
//        try {
//            //初始化代码
//            ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader("/");
//            //获取模板
//            GroupTemplate gt = new GroupTemplate(resourceLoader, Configuration.defaultConfiguration());
//            Template template = gt.getTemplate("openlayers2.html");
//            //渲染结果
//            template.binding(args);
//            String str = template.render();
//
//            //保存文本到文件
//            Path rootLocation = Paths.get(this.output_folder);
//            if (Files.notExists(rootLocation)) Files.createDirectories(rootLocation);
//            //data.js是文件
//            Path path = rootLocation.resolve("openlayers2.html");
//            byte[] strToBytes = str.getBytes();
//            Files.write(path, strToBytes);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    /**
//     * Generation of the base tiles (the lowest in the pyramid) directly from the input raster
//     *
//     * @param tileDetails
//     */
//    private TileJobInfo generate_base_tiles(List<TileDetail> tileDetails) throws SQLException {
//        logger.info("Generating Base Tiles:");
//
//        logger.info("Tiles generated from the max zoom level:");
//
//        int tminx = this.tminmax.get(this.tmaxz)[0];
//        int tminy = this.tminmax.get(this.tmaxz)[1];
//        int tmaxx = this.tminmax.get(this.tmaxz)[2];
//        int tmaxy = this.tminmax.get(this.tmaxz)[3];
//
//        Dataset ds = this.out_ds;
//
//        int tilebands = this.dataBandsCount + 1;
//        int querysize = this.querysize;
//
//        int tcount = (1 + Math.abs(tmaxx - tminx)) * (1 + Math.abs(tmaxy - tminy));
//
//        int ti = 0;
//
//        int tz = this.tmaxz;
//
//        for (int ty = tmaxy; ty > tminy - 1; ty--) {
//            for (int tx = tminx; tx < tmaxx + 1; tx++) {
//                if (this.stopped) {
//                    break;
//                }
//
//                ti += 1;
//
//                double[] b = null;
//                if (this.profile == EnumProfile.mercator) {
//                    b = this.mercator.tileBounds(tx, ty, tz);
//                } else if (this.profile == EnumProfile.geodetic) {
//                    b = this.geodetic.tileBounds(tx, ty, tz);
//                }
//
//                int rx = 0, ry = 0, rxsize = 0, rysize = 0, wx = 0, wy = 0, wxsize = 0, wysize = 0;
//
//                if (this.profile == EnumProfile.mercator || this.profile == EnumProfile.geodetic) {
//                    int[][] rbwb = this.geo_query(ds, b[0], b[3], b[2], b[1], 0);
//
//                    rbwb = this.geo_query(ds, b[0], b[3], b[2], b[1], querysize);
//
//                    rx = rbwb[0][0];
//                    ry = rbwb[0][1];
//                    rxsize = rbwb[0][2];
//                    rysize = rbwb[0][3];
//                    wx = rbwb[1][0];
//                    wy = rbwb[1][1];
//                    wxsize = rbwb[1][2];
//                    wysize = rbwb[1][3];
//                } else if (this.profile == EnumProfile.raster) {
//                    int tsize = (int) this.tsize[tz];//tilesize in raster coordinates for actual zoom
//                    int xsize = this.out_ds.getRasterXSize();//size of the raster in pixels
//                    int ysize = this.out_ds.getRasterYSize();
//                    if (tz >= this.nativezoom) {
//                        querysize = this.tilesize;//int(2 * * (self.nativezoom - tz) * self.tilesize)
//                    }
//
//                    rx = (tx) * tsize;
//                    rxsize = 0;
//                    if (tx == tmaxx)
//                        rxsize = xsize % tsize;
//                    if (rxsize == 0)
//                        rxsize = tsize;
//
//                    rysize = 0;
//                    if (ty == tmaxy)
//                        rysize = ysize % tsize;
//                    if (rysize == 0)
//                        rysize = tsize;
//                    ry = ysize - (ty * tsize) - rysize;
//
//                    wx = 0;
//                    wy = 0;
//                    wxsize = (int) (rxsize / (float) (tsize) * this.tilesize);
//                    wysize = (int) (rysize / (float) (tsize) * this.tilesize);
//
//                    if (wysize != this.tilesize)
//                        wy = this.tilesize - wysize;
//                }
//                ///开始处理图片了//////////////////////////////////////////////////////////////////////
//
//                tileDetails.add(new TileDetail(tx, ty, tz, rx, ry, rxsize, rysize, wx, wy, wxsize, wysize, querysize));
//            }
//        }
//
//        return new TileJobInfo(
//                this.input,
//                this.dataBandsCount,
//                this.output_folder,
//                this.tileext,
//                this.tilesize,
//                this.tiledriver,
//                this.kml,
//                this.tminmax,
//                this.tminz,
//                this.tmaxz,
//                this.in_srs_wkt,
//                this.out_gt,
//                this.ominy,
//                false,
//                null,
//                false
//        );
//    }
//
//    private void scale_query_to_tile(Dataset dsquery, Dataset dstile, String tilefilename) {
//        int querysize = dsquery.GetRasterXSize();
//        int tilesize = dstile.getRasterXSize();
//        int tilebands = dstile.getRasterCount();
//
//        if (this.resampling == EnumResampling.GRA_Average) {
//            for (int i = 1; i <= tilebands; i++) {
//                int res = gdal.RegenerateOverview(dsquery.GetRasterBand(i), dstile.GetRasterBand(i), "average");
//                if (res != 0)
//                    logger.error(String.format("RegenerateOverview() failed on %s, error %f", tilefilename, res));
//            }
//        } else if (this.resampling == EnumResampling.Other) {//antialias
////            # Scaling by PIL (Python Imaging Library) - improved Lanczos
////            array = numpy.zeros((querysize, querysize, tilebands), numpy.uint8)
////            for i in range(tilebands):
////            array[:, :, i] = gdalarray.BandReadAsArray(dsquery.GetRasterBand(i + 1), 0, 0, querysize, querysize)
////            im = Image.fromarray(array, 'RGBA')  # Always four bands
////            im1 = im.resize((tilesize, tilesize), Image.ANTIALIAS)
////            if os.path.exists(tilefilename):
////            im0 = Image.open(tilefilename)
////            im1 = Image.composite(im1, im0, im1)
////            im1.save(tilefilename, self.tiledriver)
//        } else {
//            dsquery.SetGeoTransform(new double[]{0.0, tilesize / (double) querysize, 0.0, 0.0, 0.0, tilesize / (double) querysize});
//            dstile.SetGeoTransform(new double[]{0.0, 1.0, 0.0, 0.0, 0.0, 1.0});
//            int res = gdal.ReprojectImage(dsquery, dstile, null, null, this.resampling.getValue());
//            if (res != 0)
//                logger.error("ReprojectImage() failed on %s, error %f", tilefilename, res);
//        }
//    }
//
//    private int[][] geo_query(Dataset ds, double ulx, double uly, double lrx, double lry, int querysize) {
//        double[] geotran = ds.GetGeoTransform();
//        int rx = (int) ((ulx - geotran[0]) / geotran[1] + 0.001);
//        int ry = (int) ((uly - geotran[3]) / geotran[5] + 0.001);
//        int rxsize = (int) ((lrx - ulx) / geotran[1] + 0.5);
//        int rysize = (int) ((lry - uly) / geotran[5] + 0.5);
//
//        int wxsize, wysize;
//        if (querysize == 0) {
//            wxsize = rxsize;
//            wysize = rysize;
//        } else {
//            wxsize = querysize;
//            wysize = querysize;
//        }
//
//        int wx = 0;
//        if (rx < 0) {
//            int rxshift = Math.abs(rx);
//            wx = (int) (wxsize * ((float) (rxshift) / rxsize));
//            wxsize = wxsize - wx;
//            rxsize = rxsize - (int) (rxsize * ((float) (rxshift) / rxsize));
//            rx = 0;
//        }
//        if ((rx + rxsize) > ds.getRasterXSize()) {
//            wxsize = (int) (wxsize * ((float) (ds.getRasterXSize() - rx) / rxsize));
//            rxsize = ds.getRasterXSize() - rx;
//        }
//
//        int wy = 0;
//
//        if (ry < 0) {
//            int ryshift = Math.abs(ry);
//            wy = (int) (wysize * ((float) (ryshift) / rysize));
//            wysize = wysize - wy;
//            rysize = rysize - (int) (rysize * ((float) (ryshift) / rysize));
//            ry = 0;
//        }
//
//        if ((ry + rysize) > ds.getRasterYSize()) {
//            wysize = (int) (wysize * ((float) (ds.getRasterYSize() - ry) / rysize));
//            rysize = ds.getRasterYSize() - ry;
//        }
//        return new int[][]{new int[]{rx, ry, rxsize, rysize}, new int[]{wx, wy, wxsize, wysize}};
//    }
//
//    private double log2(double x) {
//        return Math.log10(x) / Math.log10(2);
//    }
//
//    private BoundingBox getBoundBox() {
//        Envelope envelope = reader.getEnvelope();
//        if (this.profile == EnumProfile.mercator) {
//            envelope = reader.getEnvelope();
//            double[] min = lonLat2Mercator(envelope.getMinX(), envelope.getMinY());
//            double[] max = lonLat2Mercator(envelope.getMaxX(), envelope.getMaxY());
//            envelope = new Envelope();
//            envelope.init(min[0], max[0], min[1], max[1]);
//        }
//
//        return new BoundingBox(envelope.getMinX(), envelope.getMaxX(), envelope.getMaxX(), envelope.getMaxY());
//    }
//
//    public double[] lonLat2Mercator(double lon, double lat) {
//        double x = lon * 20037508.342789 / 180;
//        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360.0)) / (Math.PI / 180);
//        y = y * 20037508.34789 / 180;
//        return new double[]{x, y};
//    }
//
//    private String[] parse_args(String[] args) {
//        return new String[]{args[args.length - 1], args[args.length - 2]};
//    }
//
//    private Map<String, String> parse_options(String[] args) {
//        Map<String, String> options = new HashMap<>();
//        BasicParser parser = new BasicParser();
//        CommandLine cl;
//        try {
//            cl = parser.parse(this.parser, args);
//            if (cl.getOptions().length > 0) {
//                for (Option option : cl.getOptions()) {
//                    options.put(option.getLongOpt(), cl.getOptionValue(option.getOpt()));
//                }
//            }
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//        //初始化默认值
////        verbose=False, profile="mercator", kml=False, url='',
////                webviewer='all', copyright='', resampling='average', resume=False,
////                googlekey='INSERT_YOUR_KEY_HERE', bingkey='INSERT_YOUR_KEY_HERE'
//
//        if (!options.containsKey("verbose"))
//            options.put("verbose", "false");//是否输出日志
//        if (!options.containsKey("profile"))
//            options.put("profile", "mercator");//切片的模式
//        if (!options.containsKey("kml"))
//            options.put("kml", "false");//是否输出KML
//        if (!options.containsKey("url"))
//            options.put("url", "");//地址
//        if (!options.containsKey("webviewer"))
//            options.put("webviewer", "all");
//        if (!options.containsKey("copyright"))
//            options.put("copyright", "");
//        if (!options.containsKey("resampling"))
//            options.put("resampling", "average");//重采样模式
//        if (!options.containsKey("resume"))
//            options.put("resume", "false");
//        if (!options.containsKey("googlekey"))
//            options.put("googlekey", "INSERT_YOUR_KEY_HERE");
//        if (!options.containsKey("bingkey"))
//            options.put("bingkey", "INSERT_YOUR_KEY_HERE");
//        return options;
//    }
//
//    public EnumResampling getResampling() {
//        return resampling;
//    }
//
//    public void setResampling(EnumResampling resampling) {
//        if (resampling == EnumResampling.Other) {
//        } else if (resampling == EnumResampling.GRA_NearestNeighbour) {
//            this.resampling = Enum.valueOf(EnumResampling.class, String.valueOf(gdalconst.GRA_NearestNeighbour));
//            this.querysize = this.tilesize;
//        } else if (resampling == EnumResampling.GRA_Bilinear) {
//            this.resampling = Enum.valueOf(EnumResampling.class, String.valueOf(gdalconst.GRA_Bilinear));
//            this.querysize = this.tilesize * 2;
//        } else if (resampling == EnumResampling.GRA_Cubic)
//            this.resampling = Enum.valueOf(EnumResampling.class, String.valueOf(gdalconst.GRA_Cubic));
//        else if (resampling == EnumResampling.GRA_CubicSpline)
//            this.resampling = Enum.valueOf(EnumResampling.class, String.valueOf(gdalconst.GRA_CubicSpline));
//        else if (resampling == EnumResampling.GRA_Lanczos)
//            this.resampling = Enum.valueOf(EnumResampling.class, String.valueOf(gdalconst.GRA_Lanczos));
//        this.resampling = resampling;
//    }
//
//    public void worker_tile_details() {
//
//    }
//
//    private class BaseTileTask implements Runnable {
//        private TileDetail tileDetail;
//        private TileJobInfo tileJobInfo;
//
//        public BaseTileTask(TileJobInfo tileJobInfo, TileDetail tileDetail) {
//            this.tileJobInfo = tileJobInfo;
//            this.tileDetail = tileDetail;
//        }
//
//        @Override
//        public void run() {
//            create_base_tile(tileJobInfo, tileDetail);
//        }
//    }
//
//}
