# Installing and updating the game files _without mods_
**1.** The game files are going to be placed in your phones storage. In order
to execute them later, this app needs your permission to access this storage.
Make sure now that the app has the "Storage" permission enabled.


**2.** For the next steps of this guide, you need to have Java installed on your PC.

On the PC, press [Windows-Key] + R and type "cmd" in the window that pops up. Press enter.
Now, type "java -version" and press enter.

If an error message pops up, you need to install Java from the website https://java.com .
If not, congratulations! You have Java installed!

**3.** Next, we have to get the game files. First, create a new folder on your PC anywhere you like.

Now you have to find the game files. The location of them can vary depending on how you installed CrossCode.
If you used Steam, the location should be "**C:\Program Files (x86)\Steam\steamapps\common\CrossCode**"
If you used a different method (GOG, itch.io, etc.), search on the internet where games for that are
usually saved.

When you've found the game files, copy the "assets" folder to the folder you just created.

**4.** CrossAndroid can't use the game files as they are now. To convert the game
files into a format that the app can read, we are going to use a tool called "Jobbifier".

Open https://www.github.com/monkey0506/jobbifier/releases . Click on the links for jobb.jar
and jObbifier.jar and download them to your created folder.

You should now have in this folder:
- the "assets" folder from the game
- the "jobb.jar"
- the "jObbifier.jar"

**5.** Now, everything is ready. Double-click jObbifier.jar to execute it.

Enter the following:
- Input folder: &lt;the copied game assets folder&gt;
- Output location: &lt;your created folder&gt;
- Package name: de.radicalfishgames.crosscode
- Package version: 1
- Use password: No

Select "main" next to the button. Now, press "Create OBB". The tool will start converting
the game files, this may take a minute.

When it is done, close the program and proceed with the next step.

**6.** Almost done! The tool should have created a file called
"**main.1.de.radicalfishgames.crosscode.obb**" - this is the file this app can read.
Now we just need to move it to the right place on your phone.

To do this, connect your phone to your PC. If your PC doesn't display the phone as connected,
pull down the notification bar of the phone and tap the "Phone is charging via USB" notification.
 Select "File transfer" in the menu that pops up. You PC should now be able to "see" the phone.

Now, move the file "main.1.de.radicalfishgames.crosscode.obb" from your PC to the folder
"**Android/obb/de.radicalfishgames.crosscode**" on your phone. Create it if it doesn't exist yet.

That's it. You are done!

You can now delete the folder that you created at the beginning of this guide.
