@echo off
echo ====================================================
echo Instalando dependencias locales para el proyecto
echo ====================================================

:: Librerias internas de NetBeans
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\NetBeansProjects\libsVirtual\dist\libsVirtual.jar" -DgroupId=ApiVirtualEmpresas -DartifactId=libsVirtual -Dversion=1.0.0 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\NetBeansProjects\libSMS\dist\libSMS.jar" -DgroupId=ApiVirtualEmpresas -DartifactId=libSMS -Dversion=3.0.0 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libEmailSmsNew\libEmailSms.jar" -DgroupId=ApiVirtualEmpresas -DartifactId=libEmailSms -Dversion=13.0.0 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\NetBeansProjects\libClavesCore\dist\libClavesCore.jar" -DgroupId=ApiVirtualEmpresas -DartifactId=libClavesCore -Dversion=3.0.0 -Dpackaging=jar

:: Librerias SRI / MITyC
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibAPI-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibAPI -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibCert-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibCert -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibCrypt-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibCrypt -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibOCSP-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibOCSP -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibPolicy-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibPolicy -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibTrust-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibTrust -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibTSA-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibTSA -Dversion=1.1.7 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\MITyCLibXADES-1.1.7.jar" -DgroupId=sdx.sri -DartifactId=MITyCLibXADES -Dversion=1.1.7 -Dpackaging=jar

:: Bouncy Castle
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\bcprov-jdk16-1.45.jar" -DgroupId=sdx.sri -DartifactId=bcprov-jdk16 -Dversion=1.45 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\bcmail-jdk16-1.45.jar" -DgroupId=sdx.sri -DartifactId=bcmail-jdk16 -Dversion=1.45 -Dpackaging=jar
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\bctsp-jdk16-1.45.jar" -DgroupId=sdx.sri -DartifactId=bctsp-jdk16 -Dversion=1.45 -Dpackaging=jar

:: XMLSec
call mvn install:install-file -Dfile="C:\libsVirtualEmpresas\libreriasSdxSri\lib\xmlsec-1.4.2-ADSI-1.1.jar" -DgroupId=xmlsec -DartifactId=xmlsec -Dversion=1.4.2-ADSI-1.1 -Dpackaging=jar

echo ====================================================
echo Instalacion finalizada con exito!
echo ====================================================
