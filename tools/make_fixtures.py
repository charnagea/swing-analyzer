"""Build the core's test fixtures from a sample swing video.

Runs MediaPipe Pose Landmarker (the same .task model the app bundles) in VIDEO mode over every frame,
writes the landmarks in the app's track format, and saves grayscale crops around the hands near the
top of the swing for the shaft finder.

Usage:
  python tools/make_fixtures.py <video.mp4> <pose_landmarker_heavy.task> <out_dir> [--crop-from N --crop-to M]

Requires: pip install mediapipe opencv-python-headless numpy
"""
import argparse
import json
import os

import cv2
import numpy as np
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

ap = argparse.ArgumentParser()
ap.add_argument("video")
ap.add_argument("model")
ap.add_argument("out")
ap.add_argument("--crop-from", type=int, default=36)
ap.add_argument("--crop-to", type=int, default=60)
a = ap.parse_args()
os.makedirs(os.path.join(a.out, "crops"), exist_ok=True)

opts = vision.PoseLandmarkerOptions(
    base_options=mp_python.BaseOptions(model_asset_path=a.model),
    running_mode=vision.RunningMode.VIDEO,
    num_poses=1,
)
cap = cv2.VideoCapture(a.video)
w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
fps = cap.get(cv2.CAP_PROP_FPS)
frames, grays = [], {}
with vision.PoseLandmarker.create_from_options(opts) as lm:
    i = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        t_ms = cap.get(cv2.CAP_PROP_POS_MSEC)
        res = lm.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)), int(round(t_ms)))
        if res.pose_landmarks:
            p = res.pose_landmarks[0]
            frames.append({"i": i, "t": round(t_ms / 1000.0, 4), "found": True,
                           "x": [round(q.x * w, 1) for q in p], "y": [round(q.y * h, 1) for q in p],
                           "v": [round(q.visibility, 2) for q in p]})
        else:
            frames.append({"i": i, "t": round(t_ms / 1000.0, 4), "found": False,
                           "x": [None] * 33, "y": [None] * 33, "v": [0] * 33})
        if a.crop_from <= i <= a.crop_to:
            grays[i] = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        i += 1

with open(os.path.join(a.out, "landmarks.json"), "w") as f:
    json.dump({"w": w, "h": h, "fps": fps, "frames": frames}, f, separators=(",", ":"))

# Generous crops around the hands; the app requests smaller ones inside these.
sh = np.array([[(fr["x"][11] + fr["x"][12]) / 2, (fr["y"][11] + fr["y"][12]) / 2] for fr in frames[:9]])
hp = np.array([[(fr["x"][23] + fr["x"][24]) / 2, (fr["y"][23] + fr["y"][24]) / 2] for fr in frames[:9]])
torso = float(np.mean(np.linalg.norm(sh - hp, axis=1)))
index = []
for i, g in grays.items():
    fr = frames[i]
    hx = (fr["x"][15] + fr["x"][16]) / 2
    hy = (fr["y"][15] + fr["y"][16]) / 2
    x0 = int(max(0, hx - 1.6 * torso)); x1 = int(min(w, hx + 1.6 * torso))
    y0 = int(max(0, hy - 1.6 * torso)); y1 = int(min(h, hy + 0.6 * torso))
    crop = g[y0:y1, x0:x1]
    name = f"f{i:03d}.pgm"
    cv2.imwrite(os.path.join(a.out, "crops", name), crop)
    index.append({"frame": i, "x0": x0, "y0": y0, "w": x1 - x0, "h": y1 - y0, "file": name})
with open(os.path.join(a.out, "crops.json"), "w") as f:
    json.dump(index, f, indent=1)
print(f"{len(frames)} frames, {len(index)} crops, torso {torso:.1f} px, {fps:.2f} fps")
