This repository contains the android code for research project "Detective Echo" conducted during 2019 Fall, in CS442. The code is based on [paramsen's open-source project](https://github.com/paramsen/noise). 

# Build process

To compile the project, please first clone this project.

```
git clone git@github.com:cjeon/442Client.git
```

Then, to get the kissfft codes, do the following:

1. Run `git submodule init; git submodule update` in project root
2. Check that kissfft exists in `noise/src/native/kissfft`

Then open the root directory on Android studio.

Then there are three modules in the project. Your main concern is the `sample` module. Compile the `sample` module to replicate our results.

# App features

Install the app on an Android device. You must grant necessary permissions for app to function normally.

There are three buttons on the top. "record", "export", "configurations", accordingly.

## Configurations

First, set environment variables by clicking "configurations". Below are our experiment settings.

```
{
sample_count: 1000,
signal_length: 50,
signal_recording_length: 50,
tail_recording_length: 50
}
```

However, note that a sample count of 1000 would result in long experiment time, so if you just want to test the function of the app, 
you should set it to a lower number, such as 5 or 10.

If you want to get some real data, but you want to do it quickly, sample count of around 300 would give you reasonable model performance.

## Recording

First, in the configurations, set the name of the file to be saved. This typically is the name of the object (for example: pen, can etc). You must change the filename for each class.

**Please do not forget to change the filename in the configurations before recording, since the data will be overriden without asking.**

Then you should record the signals by clicking the "record" button. Please put the device about 10cm far from the object you want to classify.
Please put your volume to lowest (but do not mute it). Please do the recording in the quite and noise-free environment (and try not to make noise yourself). Please try to keep the location of the phone same while recording the multiple classes' echos. 

## Exporting

After you're done, you can export the data using the "export" button. The app will zip the output files and ask you to choose where to export to. Please note that if you have too many data (in our final experiment, we had ~1GB data) the zip process may take long. Please be patient. When the export prompt comes up, choose the place to export the data to. We mainly used the Google Drive.

## Misc

When you have made some mistake and want to wipe data, use "wipe all text files" function in the configuration.

Please do not forget to change the filename in the configurations before recording, since the data will be overriden without asking.


