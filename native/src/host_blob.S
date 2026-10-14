// The Java plugin host (re/host/host.jar, built by host/build.sh), embedded as a blob.
    .section .rodata.osrsrl_host, "a"
    .global osrsrl_host_jar
    .global osrsrl_host_jar_end
    .hidden osrsrl_host_jar
    .hidden osrsrl_host_jar_end
    .balign 16
osrsrl_host_jar:
    .incbin HOST_JAR
osrsrl_host_jar_end:
