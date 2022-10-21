package com.sk.bds.ticket.api.controller;

import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.RequestBodyParam;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.response.AppResponse;
import com.sk.bds.ticket.api.service.AdminService;
import com.sk.bds.ticket.api.service.TicketService;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.DateValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@RestController
@RequestMapping(value = "admin")
public class AdminController {
    public static final String REQUEST_TIME_FORMAT = "yyyyMMdd";
    public static final String LOG_FILE_TIME_FORMAT = "yyyy-MM-dd";
    public static final String CURRENT_LOG_NAME = "ticket-debug.log";

    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = {"logs", "getLogFileNames"})
    public Callable<Object> listLogs(@RequestParam(name = "date", required = false, defaultValue = "") String targetDate) {
        return () -> {
            File logDir = new File(AppConfig.getAppLogPath());
            if (logDir.exists() && logDir.isDirectory()) {
                JSONArray resultArray = new JSONArray();
                if (targetDate != null && targetDate.trim().length() > 0) {
                    if (targetDate.trim().length() == REQUEST_TIME_FORMAT.length()) {
                        DateValidator validator = DateValidator.getInstance();
                        if (validator.isValid(targetDate, REQUEST_TIME_FORMAT)) {
                            SimpleDateFormat requestDateFormat = new SimpleDateFormat(REQUEST_TIME_FORMAT);
                            Date logDate = requestDateFormat.parse(targetDate.trim());
                            List<File> logFiles = getLogFilesOfDate(logDate);
                            for (File file : logFiles) {
                                String fileName = file.getName();
                                if (fileName.endsWith("log") || fileName.endsWith("zip")) {
                                    JSONArray item = new JSONArray();
                                    item.put(fileName);
                                    item.put(file.length() + " Bytes");
                                    resultArray.put(item);
                                }
                            }
                        } else {
                            throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid date format transferred. " + targetDate + " Should be use yyyyMMdd."));
                        }
                    } else {
                        throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid date format transferred. " + targetDate + " Should be use yyyyMMdd."));
                    }
                } else {
                    File[] logFiles = logDir.listFiles();
                    Util.sortFileByName(logFiles);
                    for (File file : logFiles) {
                        String fileName = file.getName();
                        if (fileName.endsWith("log") || fileName.endsWith("zip")) {
                            JSONArray item = new JSONArray();
                            item.put(fileName);
                            item.put(file.length() + " Bytes");
                            resultArray.put(item);
                        }
                    }
                }
                return resultArray.toString();
            } else {
                return AppResponse.from();
            }
        };
    }

    @GetMapping(path = {"logs/{target}", "getLog/{target}"})
    public ResponseEntity<StreamingResponseBody> downloadLog(@ApiParam(required = true, value = "target: Log File Name 또는 대상 날짜(yyyyMMdd) (필수 값)") @PathVariable("target") String target) throws AppError.BadRequest {
        //Content type header configuration
        //https://stackoverflow.com/questions/20508788/do-i-need-content-type-application-octet-stream-for-file-download
        //Content-Type: application/octet-stream
        //Content-Disposition: attachment; filename="picture.png"
        //	=> Means "I don't know what the hell this is. Please save it as a file, preferably named picture.png".
        //
        //Content-Type: image/png
        //Content-Disposition: attachment; filename="picture.png"
        //	=> Means "This is a PNG image. Please save it as a file, preferably named picture.png".
        //
        //Content-Type: image/png
        //Content-Disposition: inline; filename="picture.png"
        //	=> Means "This is a PNG image. Please display it unless you don't know how to display PNG images. Otherwise, or if the user chooses to save it, we recommend the name picture.png for the file you save it as".

        StreamingResponseBody streamResponseBody = null;
        if (target.endsWith("log") || target.endsWith("zip")) {
            File logFile = new File(AppConfig.getAppLogPath(), target);
            if (logFile.exists() && logFile.isFile()) {
                streamResponseBody = new StreamingResponseBody() {
                    @Override
                    public void writeTo(OutputStream outStream) throws IOException {
                        File logFile = new File(AppConfig.getAppLogPath(), target);
                        if (logFile.exists() && logFile.isFile()) {
                            if (target.equals(CURRENT_LOG_NAME)) {
                                File tempFile = new File(AppConfig.getAppLogPath(), target + "." + System.currentTimeMillis());
                                FileCopyUtils.copy(logFile, tempFile);
                                final InputStream inStream = new FileInputStream(tempFile);
                                try {
                                    Util.readAndWrite(inStream, outStream);
                                } catch (IOException e) {
                                    Util.ignoreException(e);
                                } finally {
                                    inStream.close();
                                    log.info("Temporary log file deleting... {}", tempFile.getName());
                                    FileUtils.deleteQuietly(tempFile);
                                }
                            } else {
                                final InputStream inStream = new FileInputStream(logFile);
                                try {
                                    Util.readAndWrite(inStream, outStream);
                                } catch (IOException e) {
                                    log.error("Stream output failed. {}", e);
                                } finally {
                                    inStream.close();
                                }
                            }
                        }
                    }
                };
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment;filename=" + target)
                        .body(streamResponseBody);
            } else {
                throw new AppError.BadRequest(TicketUtil.internalErrorText(" Log file is not exists. " + target));
            }
        } else {
            if (target.trim().length() == REQUEST_TIME_FORMAT.length()) {
                DateValidator validator = DateValidator.getInstance();
                if (validator.isValid(target, REQUEST_TIME_FORMAT)) {
                    try {
                        final SimpleDateFormat requestDateFormat = new SimpleDateFormat(REQUEST_TIME_FORMAT);
                        final Date targetDate = requestDateFormat.parse(target.trim());
                        final String outputZipFileName = target + "-log.zip";
                        final List<File> logFiles = getLogFilesOfDate(targetDate);
                        if (logFiles.size() == 0) {
                            log.error("Log file is not exists for {}", target);
                            throw new AppError.BadRequest(TicketUtil.internalErrorText(" Log file is not exists. " + target));
                        }
                        streamResponseBody = new StreamingResponseBody() {
                            @Override
                            public void writeTo(OutputStream outStream) throws IOException {
                                File outputFile;
                                boolean deleteTempZipFile = false;
                                if (logFiles.size() == 1 && logFiles.get(0).getName().endsWith("zip")) {
                                    //No compress.
                                    outputFile = logFiles.get(0);
                                } else {
                                    //Compress
                                    outputFile = new File(outputZipFileName);
                                    Util.zipFiles(logFiles, outputFile);
                                    deleteTempZipFile = true;
                                }
                                final InputStream inStream = new FileInputStream(outputFile);
                                try {
                                    Util.readAndWrite(inStream, outStream);
                                } catch (IOException e) {
                                    log.error("Stream output failed. {}", e);
                                } finally {
                                    inStream.close();
                                }
                                if (deleteTempZipFile) {
                                    log.info("Temporary zip file deleting... {}", outputFile.getName());
                                    FileUtils.deleteQuietly(outputFile);
                                }
                            }
                        };
                        //https://en.wikipedia.org/wiki/Media_type
                        //final String ZipMediaType = "application/zip";
                        //MediaType mediaType = MediaType.parseMediaType(ZipMediaType);
                        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_OCTET_STREAM) //.contentType(mediaType)
                                .header("Content-Disposition", "attachment;filename=" + outputZipFileName)
                                .body(streamResponseBody);

                    } catch (ParseException e) {
                        throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid date format transferred. " + target + " Should be use yyyyMMdd."));
                    }
                } else {
                    throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid date format transferred. " + target + " Should be use yyyyMMdd."));
                }
            } else {
                throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid target transferred. " + target));
            }
        }
    }

    @PostMapping(path = {"logs/{target}/delete"})
    public Callable<Object> deleteLog(@ApiParam(required = true, value = "target: Log File Name 또는 대상 날짜(yyyyMMdd) (필수 값)") @PathVariable("target") String target) throws AppError.BadRequest {
        return () -> {
            if (target.endsWith("log")) {
                if (!target.equals(CURRENT_LOG_NAME)) {
                    Util.deleteFile(target);
                }
            } else {
                String dateFilter = null;
                if (target.trim().length() == REQUEST_TIME_FORMAT.length()) {
                    DateValidator validator = DateValidator.getInstance();
                    if (validator.isValid(target, REQUEST_TIME_FORMAT)) {
                        SimpleDateFormat requestDateFormat = new SimpleDateFormat(REQUEST_TIME_FORMAT);
                        SimpleDateFormat fileNameDateFormat = new SimpleDateFormat(LOG_FILE_TIME_FORMAT);
                        try {
                            Date date = requestDateFormat.parse(target.trim());
                            dateFilter = fileNameDateFormat.format(date);
                        } catch (ParseException e) {
                            throw new AppError.BadRequest(TicketUtil.internalErrorText(" Invalid target date format(). " + target));
                        }
                    }
                }

                if (dateFilter != null) {
                    final File logDir = new File(AppConfig.getAppLogPath());
                    if (logDir.exists() && logDir.isDirectory()) {
                        File[] logFiles = logDir.listFiles();
                        if (logFiles != null) {
                            Util.sortFileByName(logFiles);
                            for (File logFile : logFiles) {
                                String fileName = logFile.getName();
                                if (fileName.endsWith("log") && fileName.contains(dateFilter)) {
                                    Util.deleteFile(fileName);
                                }
                            }
                        }
                    }
                }
            }
            return AppResponse.from();
        };
    }

    @Autowired
    TicketService ticketService;

    @Autowired
    AdminService adminService;

    @GetMapping(path = {"checkCspTicket/{freshdeskTicketId}"})
    public Callable<Object> checkCspTicket(@ApiParam(required = true, value = "freshdeskTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("freshdeskTicketId") String freshdeskTicketId) {
        return () -> {

            // if (!ticketService.isServiceInitialized())
            //    throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");

            JSONObject response = adminService.checkCspTicket(freshdeskTicketId);
            String responseText = StringUtils.replace(response.toString(), "\"", "\'");
            HashMap<String, Object> data = new HashMap<>();
            String Key = String.format("TicketId:%s 검증결과",freshdeskTicketId);
            data.put(Key, responseText);
            return AppResponse.from(data);
        };
    }

    private static List<File> getLogFilesOfDate(Date targetDate) {
        List<File> dateLogFileList = new ArrayList<>();
        File logDir = new File(AppConfig.getAppLogPath());
        if (logDir.exists() && logDir.isDirectory()) {
            SimpleDateFormat fileNameDateFormat = new SimpleDateFormat(LOG_FILE_TIME_FORMAT);
            String dateFilter = fileNameDateFormat.format(targetDate);
            String today = fileNameDateFormat.format(new Date());
            boolean includeCurrentLogFile = dateFilter.equals(today);

            File[] logFiles = logDir.listFiles();
            Util.sortFileByName(logFiles);

            for (File file : logFiles) {
                String fileName = file.getName();
                if (fileName.endsWith("log") || fileName.endsWith("zip")) {
                    if (fileName.contains(dateFilter) || (includeCurrentLogFile && fileName.equals(CURRENT_LOG_NAME))) {
                        dateLogFileList.add(file);
                    }
                }
            }
        }
        return dateLogFileList;
    }
}
