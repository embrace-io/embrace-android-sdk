cmake_minimum_required(VERSION 3.10.2)

add_library(
        emb-crisps
        SHARED
        crisps.c
)

add_library(
        emb-donuts
        SHARED
        donuts.c
)
