@echo off

set "targetDir=target"

echo Creating target directory...
if not exist "%targetDir%" mkdir "%targetDir%"
if errorlevel 1 goto error

cd "%targetDir%"
if errorlevel 1 goto error

echo Cloning https://github.com/kg-construct/rml-core
git clone https://github.com/kg-construct/rml-core
if errorlevel 1 goto error

echo Cloning rhttps://github.com/kg-construct/rml-cc
git clone https://github.com/kg-construct/rml-cc
if errorlevel 1 goto error

echo Cloning https://github.com/kg-construct/rml-io
git clone https://github.com/kg-construct/rml-io
if errorlevel 1 goto error

echo Cloning rhttps://github.com/kg-construct/rml-fnml
git clone https://github.com/kg-construct/rml-fnml
if errorlevel 1 goto error

echo Cloning https://github.com/kg-construct/rml-lv
git clone https://github.com/kg-construct/rml-lv
if errorlevel 1 goto error

echo Cloning rhttps://github.com/kg-construct/rml-star
git clone https://github.com/kg-construct/rml-star
if errorlevel 1 goto error

echo All repositories cloned successfully into the target directory!

echo Copying test-case directories to ..\src\test\resources
xcopy .\rml-core\test-cases ..\src\test\resources\rml-core /E /I /Y
xcopy .\rml-cc\test-cases ..\src\test\resources\rml-cc /E /I /Y
xcopy .\target\rml-io\test-cases ..\src\test\resources\rml-io /E /I /Y
xcopy .\rml-lv\test-cases ..\src\test\resources\rml-lv /E /I /Y
xcopy .\rml-fnml\test-cases ..\src\test\resources\rml-fnml /E /I /Y
xcopy .\rml-star\test-cases ..\src\test\resources\rml-star /E /I /Y

rmdir /S /Q .\rml-core
rmdir /S /Q .\rml-cc
rmdir /S /Q .\rml-io
rmdir /S /Q .\rml-lv
rmdir /S /Q .\rml-fnml
rmdir /S /Q .\rml-star

goto end

:error
echo Error occurred.
echo Check the repository URLs and your internet connection.
echo or verify that you have permissions to create and write to the target directory.

:end
pause