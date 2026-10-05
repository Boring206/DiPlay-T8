#!/usr/bin/env python3
"""Regenerates the Traditional Chinese (Taiwan) resources from the Simplified ones.

Run after changing anything under values-zh-rCN:

    pip install opencc-python-reimplemented
    python3 scripts/make_zh_tw.py
"""
import os
import re

from opencc import OpenCC

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "res")
# Taiwan usage that the phrase table leaves in its mainland or variant form.
FIXES = [("藍芽", "藍牙"), ("影片解碼器", "視訊解碼器"), ("臺", "台"),
         ("可供應用使用", "可供 App 使用"), ("電話應用裡", "電話 App 裡"),
         ("許可權", "權限"), ("移動資料", "行動數據"), ("資料線", "傳輸線"), ("分屏", "分割畫面"),
         ("畫中畫", "子母畫面"), ("高階", "進階"), ("流量套餐", "上網方案"),
         ("USB 資料介面", "USB 資料連接埠"), ("USB 介面", "USB 連接埠"), ("高效影片", "高效率視訊")]
# "區域網" alone is the mainland form; leave an existing "區域網路" untouched.
PATTERNS = [(re.compile("區域網(?!路)"), "區域網路")]

converter = OpenCC("s2twp")
source = os.path.join(RES, "values-zh-rCN")
target = os.path.join(RES, "values-zh-rTW")
os.makedirs(target, exist_ok=True)
# Every file, not only strings.xml: a partial locale would mix Chinese with English fallbacks.
for name in sorted(os.listdir(source)):
    text = converter.convert(open(os.path.join(source, name), encoding="utf-8").read())
    for old, new in FIXES:
        text = text.replace(old, new)
    for pattern, new in PATTERNS:
        text = pattern.sub(new, text)
    open(os.path.join(target, name), "w", encoding="utf-8").write(text)
    print("wrote values-zh-rTW/" + name)
