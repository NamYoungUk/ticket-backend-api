package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.StringMatchType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.EmailValidator;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

@Slf4j
public class Util {
    public static boolean isValidEmailAddress(String email) {
        //https://blog.mailtrap.io/java-email-validation/
        //final String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w-_]+\\.)+[\\w-_]+[\\w]$";
        //final String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        //final String regex = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";
        //final String regex = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+(?:\\.[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+↵\n)*@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$";
        //final String regex = "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,6}$";
        //final String regex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        //return email.matches(regex);
        if (email != null) {
            EmailValidator validator = EmailValidator.getInstance();
            return validator.isValid(email);
        }
        return false;
    }

    public static String getLocalTimeString(String utcTimeString, String utcTimeFormat, String localTimeFormat, String localTimeZoneId) throws ParseException {
        DateFormat utcFormat = new SimpleDateFormat(utcTimeFormat);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date utcTime = utcFormat.parse(utcTimeString);
        return getLocalTimeString(utcTime, localTimeFormat, localTimeZoneId);
    }

    public static String getLocalTimeString(Date utcTime, String localTimeFormat, String localTimeZoneId) {
        DateFormat localFormat = new SimpleDateFormat(localTimeFormat);
        localFormat.setTimeZone(TimeZone.getTimeZone(localTimeZoneId));
        String localTimeString = localFormat.format(utcTime);
        //String localTimeString = utcTime.toInstant().atZone(ZoneId.of(localTimeZoneId)).format(DateTimeFormatter.ofPattern(localTimeFormat));
        return localTimeString;
    }

    public static Date parseTime(String timeString, DateFormat timeFormatter) {
        if (timeString != null && timeFormatter != null) {
            try {
                return timeFormatter.parse(timeString);
            } catch (ParseException e) {
                log.error("error: {}", e);
            }
        }
        return null;
    }

    public static String timeToString(long timeMillis) {
        if (timeMillis <= 0) {
            return "N/A";
        }

        long timeSeconds = (timeMillis / 1000);
        long timeMinutes = timeSeconds / 60;
        long timeHours = timeMinutes / 60;
        if (timeHours > 24) {
            int days = (int) (timeHours / 24);
            int hours = (int) (timeHours % 24);
            return String.format("%d d %d hr", days, hours);
        } else if (timeMinutes > 60) {
            int hour = (int) (timeMinutes / 60);
            int min = (int) (timeMinutes % 60);
            return String.format("%d hr %d min", hour, min);
        } else if (timeMinutes >= 1) {
            return String.format("%d min", timeMinutes);
        } else {
            return String.format("%d sec", timeSeconds);
        }
    }

    public static String getDayOfWeekKorString(Calendar calendar) {
        if (calendar != null) {
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            switch (dayOfWeek) {
                case Calendar.SUNDAY:
                    return "일";
                case Calendar.MONDAY:
                    return "월";
                case Calendar.TUESDAY:
                    return "화";
                case Calendar.WEDNESDAY:
                    return "수";
                case Calendar.THURSDAY:
                    return "목";
                case Calendar.FRIDAY:
                    return "금";
                case Calendar.SATURDAY:
                    return "토";
            }
        }
        return "";
    }

    public static String getDayOfWeekKorString(Date date, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance();
        if (date != null) {
            calendar.setTime(date);
        }
        if (timeZone != null) {
            calendar.setTimeZone(timeZone);
        }
        return getDayOfWeekKorString(calendar);
    }

    public static void sortFileByName(File[] files) {
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });
    }

    public static String pathName(String dirName, String fileName) {
        //File.separator:\
        //File.pathSeparator:;
        String path = "";
        if (dirName != null) {
            if (dirName.endsWith(File.separator)) {
                path = dirName;
            } else {
                path = dirName + File.separator;
            }
        }
        if (fileName != null) {
            return path + fileName;
        }
        return path;
    }

    public static boolean isExistsFile(String filePathName) {
        if (filePathName != null) {
            File f = new File(filePathName);
            return f.exists();
        }
        return false;
    }

    public static BasicFileAttributes readFileAttributes(File file) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        return attr;
    }

    public static byte[] readFileToBytes(String fileName) throws IOException {
        return Files.readAllBytes(Paths.get(fileName));
    }

    public static String readFile(String fileName) throws IOException {
        return new String(Files.readAllBytes(Paths.get(fileName)));
    }

    public static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    public static byte[] readFileToBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    public static void writeFile(String fileName, byte[] content) throws IOException {
        writeFile(fileName, content, false);
    }

    public static void writeFile(String fileName, byte[] content, boolean append) throws IOException {
        FileOutputStream writer = null;
        try {
            writer = new FileOutputStream(fileName, append);
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw e;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void writeFile(String fileName, String content) throws IOException {
        writeFile(fileName, content, false);
    }

    public static void writeFile(String fileName, String content, boolean append) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(fileName, append);
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw e;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void writeFile(File file, String content) throws IOException {
        writeFile(file, content, false);
    }

    public static void writeFile(File file, String content, boolean append) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, append);
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            throw e;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void readAndWrite(final InputStream inStream, OutputStream outStream) throws IOException {
        if (inStream != null && outStream != null) {
            byte[] data = new byte[4096];
            int read = 0;
            while ((read = inStream.read(data)) > 0) {
                outStream.write(data, 0, read);
            }
            outStream.flush();
        }
    }

    public static void deleteFile(String fileName) {
        if (fileName != null) {
            File file = new File(fileName);
            if (file.exists()) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    public static void zipFiles(List<String> sourceFiles, String zipFilePathName) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFilePathName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        for (String srcFile : sourceFiles) {
            File fileToZip = new File(srcFile);
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[4096];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
        zipOut.close();
        fos.close();
    }

    public static void zipStreams(Map<String, InputStream> sourceStreams, OutputStream outputStream) throws IOException {
        ZipOutputStream zipOut = new ZipOutputStream(outputStream);
        for (String name : sourceStreams.keySet()) {
            InputStream inputStream = sourceStreams.get(name);
            ZipEntry zipEntry = new ZipEntry(name);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[4096];
            int length;
            while ((length = inputStream.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            inputStream.close();
        }
        zipOut.close();
        outputStream.close();
    }

    public static void zipFiles(List<File> sourceFiles, File zipFilePathName) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFilePathName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        for (File fileToZip : sourceFiles) {
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[4096];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
        zipOut.close();
        fos.close();
    }

    public static void zipDirectory(String sourceDir, String zipFilePathName) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFilePathName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        File fileToZip = new File(sourceDir);
        zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.close();
        fos.close();
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[4096];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    public static long crc32(InputStream stream) throws IOException {
        if (stream != null) {
            final int bufferSize = 4096;
            CheckedInputStream checkedInputStream = new CheckedInputStream(stream, new CRC32());
            byte[] buffer = new byte[bufferSize];
            while (checkedInputStream.read(buffer, 0, buffer.length) >= 0) {
            }
            return checkedInputStream.getChecksum().getValue();
        }
        return 0;
    }

    public static long crc32(String input) {
        if (input != null) {
            byte[] bytes = input.getBytes();
            return crc32(bytes);
        }
        return 0;
    }

    public static long crc32(byte[] dataBytes) {
        if (dataBytes != null) {
            Checksum checksum = new CRC32(); // java.util.zip.CRC32
            checksum.update(dataBytes, 0, dataBytes.length);
            return checksum.getValue();
        }
        return 0;
    }

    public static void ignoreException(Exception e) {
        //Drop exception
        //log.debug("{}", e.getMessage());
    }

    public static String join(String delimiter, List<String> items) {
        //String joinedText = String.join(delimiter, items);
        StringJoiner joiner = new StringJoiner(delimiter);
        for (String item : items) {
            joiner.add(item);
        }
        return joiner.toString();
    }

    public static String trimLeft(String src) {
        if (src != null) {
            return src.replaceAll("^\\s+", "");
        }
        return null;
    }

    public static String trimRight(String src) {
        if (src != null) {
            return src.replaceAll("\\s+$", "");
        }
        return null;
    }

    public static int lastIndexOf(String pool, String findStr, int limitLength) {
        if (pool == null || findStr == null || limitLength < 1) {
            return -1;
        }
        int foundIndex = pool.indexOf(findStr, 0);
        if (foundIndex < 0 || foundIndex >= limitLength) {
            return -1;
        } else if (foundIndex == (limitLength - 1)) {
            return foundIndex;
        }

        while (true) {
            int pos = pool.indexOf(findStr, foundIndex + 1);
            if (pos < 0 || pos >= limitLength) {
                return foundIndex;
            } else {
                foundIndex = pos;
            }
        }
    }

    public static int lastIndexOf(String pool, String findStr, int beginIndex, int limitLength) {
        if (pool == null || findStr == null || limitLength < 1) {
            return -1;
        }
        int foundIndex = pool.indexOf(findStr, beginIndex);
        if (foundIndex < 0 || (foundIndex - beginIndex) > limitLength) {
            return -1;
        } else if ((foundIndex - beginIndex) == (limitLength - 1)) {
            return foundIndex;
        }

        while (true) {
            int pos = pool.indexOf(findStr, foundIndex);
            if (pos < 0 || (pos - beginIndex) >= limitLength) {
                return foundIndex;
            } else {
                foundIndex = pos;
            }
        }
    }

    public static boolean isValidUUID(String uuidString) {
        if (uuidString != null) {
            Pattern p = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[34][0-9a-fA-F]{3}-[89ab][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
            return p.matcher(uuidString).matches();
        }
        return false;
    }

    public static int getMonthsDifference(Date from, Date to) {
        if (from != null && to != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(from.getTime());
            int y1 = cal.get(Calendar.YEAR);
            int m1 = cal.get(Calendar.MONTH);
            cal.setTimeInMillis(to.getTime());
            int y2 = cal.get(Calendar.YEAR);
            int m2 = cal.get(Calendar.MONTH);
            if (from.getTime() < to.getTime()) {
                return ((y2 - y1) * 12) + (m2 - m1) + 1;
            } else {
                return ((y1 - y2) * 12) + (m1 - m2) + 1;
            }
        }
        return 0;
    }

    public static void resetTimeToZero(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(date.getTime());
            resetTimeToZero(cal);
            date.setTime(cal.getTimeInMillis());
        }
    }

    public static void resetTimeToZero(Calendar cal) {
        if (cal != null) {
            //Reset Time to 00:00:00 000
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
    }

    public static void resetTimeToMax(Date date) {
        if (date != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(date.getTime());
            resetTimeToMax(cal);
            date.setTime(cal.getTimeInMillis());
        }
    }

    public static void resetTimeToMax(Calendar cal) {
        if (cal != null) {
            //Reset Time to 23:59:59 999
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
        }
    }

    public static void setDate(Calendar cal, int year, int month, int day) {
        if (cal != null) {
            if (month < 1) {
                month = 1;
            } else if (month > 12) {
                month %= 13;
            }

            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, day);
        }
    }

    /*public static String html2text(String html) {
        if (html != null) {
            return Jsoup.parse(html).text();
        }
        return "";
    }*/

    public static String substringByBytes(String inputText, int byteLength) {
        StringBuffer result = new StringBuffer(byteLength);
        int resultLen = 0;
        //int letterCount = 0;
        for (int i = 0; i < inputText.length(); i++) {
            char c = inputText.charAt(i);
            int charLen = 0;
            if (c <= 0x7f) {
                charLen = 1;
            } else if (c <= 0x7ff) {
                charLen = 2;
            } else if (c <= 0xd7ff) {
                charLen = 3;
            } else if (c <= 0xdbff) {
                charLen = 4;
            } else if (c <= 0xdfff) {
                charLen = 0;
            } else if (c <= 0xffff) {
                charLen = 3;
            }
            if (resultLen + charLen > byteLength) {
                break;
            }
            //letterCount++;
            result.append(c);
            resultLen += charLen;
        }
        //inputText.substring(0, letterCount);
        return result.toString();
    }

    public static String cutStringByBytes(String inputText, int byteLength) throws CharacterCodingException {
        byte[] bytes = inputText.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (byteLength < buffer.limit()) {
            buffer.limit(byteLength);
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.IGNORE);
        return decoder.decode(buffer).toString();
    }

    public static boolean equalsIgnoreCase(String text1, String text2) {
        if (text1 != null && text2 != null) {
            return text1.equalsIgnoreCase(text2);
        }
        return false;
    }

    public static boolean equalsWithoutSpace(String text1, String text2) {
        if (text1 != null && text2 != null) {
            String left = text1.replaceAll("\\s", "");
            String right = text2.replaceAll("\\s", "");
            return left.equals(right);
        }
        return false;
    }

    public static boolean equalsIgnoreCaseWithoutSpace(String text1, String text2) {
        if (text1 != null && text2 != null) {
            String left = text1.replaceAll("\\s", "");
            String right = text2.replaceAll("\\s", "");
            return left.equalsIgnoreCase(right);
        }
        return false;
    }

    public static StringMatchType compareText(String text1, String text2) {
        StringMatchType matchType = StringMatchType.mismatch;
        if (text1 != null && text2 != null) {
            if (text1.equals(text2)) {
                matchType = StringMatchType.equal;
            } else if (text1.equalsIgnoreCase(text2)) {
                matchType = StringMatchType.equalIgnoreCase;
            } else if (Util.equalsWithoutSpace(text1, text2)) {
                matchType = StringMatchType.equalWithoutSpace;
            } else if (Util.equalsIgnoreCaseWithoutSpace(text1, text2)) {
                matchType = StringMatchType.equalsIgnoreCaseWithoutSpace;
            }
        }
        return matchType;
    }

    public static int compareToIgnoreCaseWithoutSpace(String text1, String text2) {
        if (text1 != null && text2 != null) {
            String left = text1.replaceAll("\\s", "");
            String right = text2.replaceAll("\\s", "");
            return left.compareToIgnoreCase(right);
        }
        return 0;
    }
}
