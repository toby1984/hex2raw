## What's this ?

This is a tiny Java program (don't be afraid, building yields a self-executable JAR) to extract the raw bytes from Intel Hex files. I wrote this to be able to use my mcs_upload C program to upload programs to an ATmega88 running an MCS bootloader.

## Building

To build the program you need

1. Maven >= 3.0
2. JDK >= 1.8

The create a self-executable JAR, just run

```
  mvn clean package
```

This will create the JAR target/hex2raw.jar

## Running

You can run the extraction using the following command-line:

```
  java -jar hex2raw.jar input.hex [output.file]
```

The output file is optional and if not present , the output file will be named 'input.raw'
