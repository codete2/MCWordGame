@echo off
echo ���ڹ��� MCWordGame ���...

REM ����Ƿ�װ��Maven
mvn -v > nul 2>&1
if errorlevel 1 (
    echo ���Ȱ�װMaven����ӵ�ϵͳ��������PATH��
    echo ���ص�ַ https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip
    pause
    exit /b 1
)

REM ����Maven����
mvn clean package

echo.
if errorlevel 1 (
    echo ����ʧ�ܣ����������Ϣ
) else (
    echo �����ɹ���
    echo ����ļ��Ѿ������� target �ļ�����
    echo �ļ���Ϊ��mcwordgame-1.0-SNAPSHOT.jar
)

pause 