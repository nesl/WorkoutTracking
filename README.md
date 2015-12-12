# WorkoutTracking

## MiLift: Autonomous and Efficient Workout Tracking Using Smartwatches
MiLift is a workout tracking service that uses commercial off-the-shelf smartwatches.
It employs a two-stage classification model that can accurately and efficiently track both cardio and weightlifting workouts without manual inputs from users.

### Android apps:
* apps/WorkoutRecorder: an Android phone app + an Android wear app to record inertial sensor data.
* apps/WorkoutManagement: an Android phone app to visualize personal workout history.
* apps/HighLevelActClassifier: an Android wear app to classify high-level activities of a user, e.g. non-workout, walking, running, and weightlifting.
It uses a set of open source components including sgd-crf (http://leon.bottou.org/projects/sgd), jahmm (https://code.google.com/p/jahmm/), and CRF++ (https://taku910.github.io/crfpp/).
* apps/WeightliftingClassifier: an Android wear app to detect and classify weightlifting activities of a user. 

### MATLAB scripts:
* analysis/autocorrelation_based.m: an autocorrelation-based weightlifting detection and rep counting algorithm.
* analysis/revisit_based.m: a revisit-based weightlifting detection and rep counting algorithm.

### Contact:
Chenguang Shen (cgshen@cs.ucla.edu) and Bo-Jhang Ho (bojhang@cs.ucla.edu)