#!/bin/bash
echo mkdir tmp, copy this to temp, and edit away this echo and following exit. lazy makefile, yes?
exit 0
cp ../kontesti.w .
nuweb -l kontesti.w
pdflatex kontesti
nuweb -l kontesti.w
pdflatex kontesti
