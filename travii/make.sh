git pull origin master
./gradlew build
cp build/distributions/svarka*universal* $HOME/release/Svarka-1.10.2-2511-server.jar
./gradlew jar
cd build/libs/
unzip svarka-*.jar
mv svarka-*/lib libraries
zip -r libraries.zip libraries
cp libraries.zip $HOME/release