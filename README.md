# CrossAndroid
Welcome to CrossAndroid! This is an app that allows you to play
CrossCode on your Android device.

Current features:
- Play CrossCode on the go!
- Support for controllers connected to your phone
- Includes a virtual controller layout _(for details see below)_
- Customize the virtual controller with size and transparency settings
  (you can even let it disable itself automatically during cutscenes)
- Haptic feedback when the screen shakes _(experimental)_
- Import/Export saves between your PC and this app! _(for details, see
  below)_

The whole project is still experimental, which is why there is no
"release" yet. But you can already try it out, use most of the games'
functionality and help improve the app by reporting bugs or making
suggestions for improvements.

## Credits
Pixelart by Ichiki Hayaite aka TheSparkstarScope

## Getting the app
First, you can download the latest test APK from
[here]( https://gitlab.com/namnodorel/crossandroid/-/jobs/artifacts/master/raw/app/build/outputs/apk/debug/app-debug.apk?job=assembleDebug).
Install it, and open the app. A guide will appear that shows you how to
get the game files from your PC, package them for the app and copy them
to your phone. 

This setup is one-time only, you can update the app later
independently of the game files.

## Using the virtual controller
Here's an overview of the combat controls overlay:

![Control scheme reference](crossandroid_overlay_reference.png)

**Important:** Do not change the controller binding settings in the
in-game options when you want to use the virtual controller! Doing so
will result in the virtual controller behaving in some weird and
unexpected ways.

Turning off the overlay will let you interact directly with the game
(click on buttons, etc.)

You can adjust the size and transparency of the virtual controller in
the settings of the app.

# Import/Export of saves
CrossAndroid handles import/export of saves with a feature called _Save
Strings_. The way this works is you can retrieve and import a "Save
String" (piece of unreadable text that contains your save data) of the
latest save. You then need to manually copy that Save String into your
other game installation, and the new save pops up at the bottom of your
saves list.

### Exporting a Save String from CrossAndroid
To export a Save String, open the app settings and toggle on "Show
Exported Save String". On the next game start, the app will now show you
a dialog that lets you copy the Save String from your latest save. You
can turn the option off again when you have retrieved your Save String.

### Importing a Save String into CrossAndroid
To import a Save String, go into the app settings and tap "Import Save
String". A dialog will appear where you can enter a previously retrieved
Save String. Paste your Save String here _(only one at a time!)_. When
you launch the game the next time, the Save String will be imported into
the game and added on the bottom of your saves list.

### Exporting/Importing a Save String from/into CrossCode on your PC
The procedure on a PC is very similar to the one with CrossAndroid. Open
the game, and press `F10` in the title screen/menu. A window will pop up
that lets you both copy the latest Save String and paste a Save String
to import into the game.

## Feedback
Speaking of suggestions: If you have any, suggest them either
[here](https://gitlab.com/Namnodorel/crossandroid/issues) or in the
#crossandroid channel on the [Community Modding Discord Server](https://discord.gg/TFs6n5v)