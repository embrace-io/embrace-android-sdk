cmake_minimum_required(VERSION 3.22.1)

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
