#!/bin/sh
# Takes the Play Store phone screenshots on a running emulator (Pixel 10 AVD, 1080x2424) with a debug build installed.
# Usage (Git Bash, from the project root): . tools/store/screenshots.sh; then call `run` per language, e.g.
#   MENU="Menu"; run en "Settings" "Fill with demo progress" "Learn 3 new" "Got it" "Check" "Drill these" "Read now" \
#       "Reading in rōmaji" "Kana chart"
# Raw shots land in /tmp/shots/<lang>_*.png; `frame <lang> <locale>` turns them into 1080x2160 (Play allows at most
# 2:1) under fastlane/metadata/android/<locale>/images/phoneScreenshots/. Run `demo_bar` first for a clean status bar.
# The drawing is done with KanjiVG strokes via `input motionevent`; coordinates assume the Pixel 10 layout.
SDK="${ANDROID_HOME:-/c/Users/$USERNAME/AppData/Local/Android/Sdk}"
STROKES="$PWD/core/src/main/resources/hiragana_strokes.txt"
export MSYS_NO_PATHCONV=1
A="$SDK/platform-tools/adb -s emulator-5554"
dump() { $A shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $A shell cat /sdcard/u.xml; }
center() { grep -o "$1[^>]*bounds=\"[^\"]*\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' '; }
tap_attr() { b=$(dump | center "$1"); [ -z "$b" ] && { echo "NOT FOUND: $1"; return 1; }; set -- $b; $A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 1.5; }
tap_text() { tap_attr "text=\"$1\""; }
tap_desc() { tap_attr "content-desc=\"$1\""; }
texts() { dump | grep -o 'text="[^"]*"' | grep -v '=""'; }
shot() { sleep 1.5; $A exec-out screencap -p > "$1"; }
scroll_down() { $A shell input swipe 540 1900 540 600 300; sleep 0.7; }
# draw <kana> <x0> <y0> <size>: draws the KanjiVG strokes of <kana> into the square at (x0,y0) of <size> px.
draw() {
  line=$(grep "^$1	" "$STROKES" | cut -f2)
  cmds=$(echo "$line" | tr ';' '\n' | awk -v x0=$2 -v y0=$3 -v s=$4 '{
    out=""; n=split($0, pts, " ");
    for (i=1;i<=n;i+=2) { split(pts[i], p, ","); x=int(x0+p[1]/109*s); y=int(y0+p[2]/109*s);
      ev = (i==1) ? "DOWN" : "MOVE"; out = out "input motionevent " ev " " x " " y "; " }
    split(pts[n], p, ","); x=int(x0+p[1]/109*s); y=int(y0+p[2]/109*s);
    out = out "input motionevent MOVE " x " " y "; input motionevent UP " x " " y "; ";
    print out }')
  $A shell "$(echo $cmds)"
}
romaji() { echo "$1" | sed 's/あ/a/g;s/い/i/g;s/う/u/g;s/え/e/g;s/お/o/g;s/か/ka/g;s/き/ki/g;s/く/ku/g;s/け/ke/g;s/こ/ko/g;s/さ/sa/g;s/し/shi/g;s/す/su/g;s/せ/se/g;s/そ/so/g;s/た/ta/g;s/ち/chi/g;s/つ/tsu/g;s/て/te/g;s/と/to/g;s/な/na/g;s/に/ni/g;s/ぬ/nu/g;s/ね/ne/g;s/の/no/g;s/は/ha/g;s/ひ/hi/g;s/ふ/fu/g;s/へ/he/g;s/ほ/ho/g;s/ま/ma/g;s/み/mi/g;s/む/mu/g;s/め/me/g;s/も/mo/g'; }
top() { for i in 1 2 3 4; do $A shell input swipe 540 700 540 2000 200; done; sleep 0.5; }
# run <lang> <Settings> <demo> <learn3> <gotit> <check> <drill> <readnow> <chart> <field>
run() {
  L=$1; out=/tmp/shots
  $A shell cmd locale set-app-locales hu.sigee.hiralearn --locales $L; sleep 1
  $A shell am force-stop hu.sigee.hiralearn; $A shell am start -W -n hu.sigee.hiralearn/.MainActivity >/dev/null; sleep 2
  tap_desc "$MENU"; tap_text "$2"; for i in 1 2 3 4 5 6; do scroll_down; done; tap_text "$3"; $A shell input keyevent BACK; sleep 1
  top; shot $out/${L}_01_home.png
  for i in 1 2; do scroll_down; done; tap_text "$4"; sleep 3; shot $out/${L}_02_intro.png
  tap_text "$5"; tap_text ya; sleep 1; tap_text や; sleep 1; tap_text "$5"; tap_text yu; sleep 1
  draw や 68 601 943; tap_text "$6"; sleep 0.3; shot $out/${L}_05_checked.png
  $A shell input keyevent BACK; sleep 1; for i in 1 2 3; do scroll_down; done
  tap_text "$7"; sleep 1.5
  q=$(texts | sed -n 2p | sed 's/text="\(.*\)"/\1/'); o1=$(texts | sed -n 3p | sed 's/text="\(.*\)"/\1/'); o2=$(texts | sed -n 4p | sed 's/text="\(.*\)"/\1/')
  if [ "$(romaji "$o1")" = "$q" ] || [ "$(romaji "$q")" = "$o1" ]; then wrong=$o2; else wrong=$o1; fi
  tap_text "$wrong"; shot $out/${L}_06_drill.png
  $A shell input keyevent BACK; sleep 1; for i in 1 2 3; do scroll_down; done
  tap_text "$8"; sleep 1.5; w=$(texts | sed -n 2p | sed 's/text="\(.*\)"/\1/'); r=$(romaji "$w")
  # A word is asked either by typing or by choice, depending on its level.
  if texts | grep -q "$9"; then tap_text "$9"; $A shell input text "$r"; sleep 0.5; tap_text "$6"; else tap_text "$r"; fi
  shot $out/${L}_07_words.png
  $A shell input keyevent BACK; sleep 1; tap_desc "$MENU"; tap_text "${10}"; sleep 1.5; shot $out/${L}_08_chart.png
  $A shell input tap 590 1810; sleep 2.5; shot $out/${L}_09_detail.png
  $A shell input keyevent BACK; sleep 0.5; $A shell input keyevent BACK; sleep 0.5
  $A shell cmd uimode night yes; sleep 2; $A shell am start -W -n hu.sigee.hiralearn/.MainActivity >/dev/null; sleep 1; top; shot $out/${L}_10_dark.png; $A shell cmd uimode night no
}
demo_bar() {
  $A shell settings put global sysui_demo_allowed 1
  for c in "enter" "clock -e hhmm 0930" "battery -e level 100 -e plugged false" "network -e mobile hide" \
           "network -e wifi show -e level 4 -e fully true" "notifications -e visible false"; do
    $A shell am broadcast -a com.android.systemui.demo -e command $c >/dev/null
  done
}
frame() { # <lang> <locale>
  E="${BROWSER_BIN:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"; T=$(mktemp -d)
  d=fastlane/metadata/android/$2/images/phoneScreenshots; rm -rf $d; mkdir -p $d; i=1
  for k in 01_home 05_checked 06_drill 02_intro 07_words 08_chart 09_detail 10_dark; do
    src="file:///$(cygpath -m /tmp/shots/$1_$k.png)"
    printf '<html><body style="margin:0;width:1080px;height:2160px;overflow:hidden;position:relative"><div style="position:absolute;inset:-40px;background:url(%s) center/cover;filter:blur(18px)"></div><img src="%s" style="position:absolute;left:59px;top:0;width:962px;height:2160px"></body></html>' "'$src'" "$src" > $T/p.html
    "$E" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1080,2160 \
        --screenshot="$(cygpath -w "$PWD/$d/${i}_${k#*_}.png")" "file:///$(cygpath -m $T/p.html)" >/dev/null 2>&1
    i=$((i+1))
  done
}
