# -*- coding:utf-8 -*-
# -*- coding:cp949 -*-
# -*- coding:euc-kr -*-

import sys
import os
import shutil
import re
import json
import csv
import math
from pprint import pprint
import requests
#import time
from datetime import date
#if not installed requests ==> run 'pip install requests' on cmd window.

today = date.today()
server_url = "https://ticket-ibm.cloudzcp.io"
DOWNLOAD_DIR = "./"+today.strftime('%Y%m%d')+"-ibm-prod-logs"+"/"

# UTF-8을 EUC-KR로 변환
def utf2euc(str):
    return unicode(str, 'utf-8').encode('euc-kr')

# EUC-KR을 UTF-8로 변환
def euc2utf(str):
    return unicode(str,'euc-kr').encode('utf-8')

# UTF-8로 변환
def toutf(str, encoding):
    return unicode(str, encoding).encode('utf-8')

def check_mk_dir(directory):
    directory = os.path.dirname(directory)
    #print (directory)
    if not os.path.exists(directory):
        os.makedirs(directory)

def http_get(url):
    print ("=================================================")
    print ("http_get:"+str(url))

    #requests module
    try:
        headers = {'authorization': 'JPkvz365YpRnQwOWQK1r', 'Content-Type': 'application/json; charset=utf-8'}
        response = requests.get(url, headers=headers)
        status_code = response.status_code
        encoding = response.encoding
        #json_dict = response.json()
        #print("type of test1:" + str(type(test1)))
        #print("download:"+str(url))
        #print ("encoding:"+str(encoding))
        result = response.text.encode(encoding, 'ignore')
        result = result.decode()
        #print ("-----------------")
        #print (result)
        #result = str(result, 'utf-8')
        #print ("-----------------3")
        #print (str(result))
        return result
    except requests.exceptions.ConnectionError:
        print ('ConnectionError')
        return None
    except Exception:
        print ('Exception')
        return None
    print ('Failed.')
    return None

def http_get_file_content(url):
    #print ("http_get_file_content:"+str(url))

    #requests module
    headers = {'authorization': 'JPkvz365YpRnQwOWQK1r', 'Content-Type': 'application/json; charset=utf-8'}
    response = requests.get(url, headers=headers, stream=True)
    return response

# data = {'outer': {'inner': 'value'}}
# res = requests.post(URL, data=json.dumps(data))
# params = {'param1': 'value1', 'param2': 'value'}
# res = requests.get(URL, params=params)
# headers = {'Content-Type': 'application/json; charset=utf-8'}
# cookies = {'session_id': 'sorryidontcare'}
# res = requests.get(URL, headers=headers, cookies=cookies)

#https://stackoverflow.com/questions/13137817/how-to-download-image-using-requests
def log_download(logFileName):
    queryUrl=server_url+"/admin/getLog/"+logFileName
    response = http_get_file_content(queryUrl)
    #print ("response: "+str(response))
    #print ("response headers: "+str(response.headers))
    if response.status_code == 200:
        filename = logFileName
        if not logFileName.endswith(".log"):
            filename = filename+".log";
            contentDisposition = response.headers['Content-Disposition']
            if contentDisposition:
                #'Content-Disposition': 'attachment;filename=20211207-log.zip'
                filenamePos = contentDisposition.find('filename=')
                filename = contentDisposition[filenamePos+len('filename='):]
                #print ("response filename: "+str(filename))
        filename = DOWNLOAD_DIR+filename
        check_mk_dir(filename)
        logFile = open(filename, 'wb')
        response.raw.decode_content = True
        shutil.copyfileobj(response.raw, logFile)

if __name__ == "__main__":
    targetFile = 'ticket-debug.log'
    #print(str(len(sys.argv)))
    print('옵션 없는 경우 : ticket-debug.log 파일을 다운로드 합니다.')
    print('list : 로그 파일 목록을 표시합니다.')
    print('all : 모든 로그 파일을 다운로드 합니다.')
    print('file_name1 file_name2 ...  : 인자로 전달된 로그 파일을 다운로드 합니다.')

    if len(sys.argv) == 1:
        print(str('기본 로그 파일을 다운로드 합니다. 파일 이름 : '+ targetFile))
        log_download(targetFile)
    elif sys.argv[1] == 'list':
        queryUrl=server_url+"/admin/getLogFileNames"
        result = http_get(queryUrl)
        jsonArray = json.loads(result)
        i = 0
        print('-------------------------------------')
        print(str(len(jsonArray))+' LOG Files')
        print('-------------------------------------')
        while i < len(jsonArray):
            print(str(jsonArray[i]))
            i = i + 1;
        print('-------------------------------------')
    elif sys.argv[1] == 'all':
        queryUrl=server_url+"/admin/getLogFileNames"
        result = http_get(queryUrl)
        jsonArray = json.loads(result)
        i = 0
        print('-------------------------------------')
        print(str(len(jsonArray))+' LOG Files')
        print('-------------------------------------')
        while i < len(jsonArray):
            print(str(jsonArray[i]))
            i = i + 1;
        print('-------------------------------------')
        i = 0
        while i < len(jsonArray):
            print(str(jsonArray[i][0])+' Downloading....')
            log_download(jsonArray[i][0])
            print(str(jsonArray[i][0])+' Downloaded')
            print('-------------------------------------')
            i = i + 1;
        print('All log files are downloaded.')
        print('-------------------------------------')
    else:
        i = 1
        while i < len(sys.argv):
            log_download(sys.argv[i])
            i = i + 1;
