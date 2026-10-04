LUNAR TAG APPLICATION - README

1. PROJECT OVERVIEW
This is an Android application named "Lunar Tag" designed for capturing geotagged and watermarked photos. All data, including photo metadata and audit logs, is stored locally on the device's database. The app does NOT save any data to a cloud database like Firestore.

The app has two primary modes:
- Normal Mode: Captures photos with the real-time timestamp.
- Custom Timestamp Mode: An "admin" feature that allows a user to pre-define a list of timestamps. When capturing photos in this mode, the app automatically assigns the next available preset timestamp to each photo in sequence.

2. REMOTE FEATURE TOGGLE (HOW TO ENABLE ADMIN MODE)
The "Custom Timestamp Mode" is hidden by default and can ONLY be enabled remotely by the app owner. This is done using a free Firebase Cloud Messaging (FCM) topic, which will never cause you to exceed any free quotas.

TO ENABLE ADMIN MODE:
1.  Go to your Firebase Project Console.
2.  In the left menu, find the "Engage" section and click on "Messaging".
3.  Click the "Create your first campaign" or "New campaign" button, and select "Notifications".
4.  In the "Notification text" section, you can put anything (e.g., "Updating config"). This will NOT be shown to the user.
5.  On the right side, under "Device preview", change the selection from "Notification" to "Data message".
6.  Under "Custom data" (key-value pairs), add the following:
    - Key: customTimestampEnabled
    - Value: true
7.  Click "Next".
8.  In the "Target" section, select "Topic".
9.  Select the topic named "feature_toggles".
10. Click "Next", then "Review", and finally "Publish" the campaign.

Within a few minutes, all installed apps will silently receive this message, save the setting, and the "Schedule Editor" will become visible to the user.

TO DISABLE ADMIN MODE:
Follow the exact same steps above, but in Step 6, set the Value to: false

3. HOW TO TEST ACCESSIBILITY AUTOMATION
The app includes an OPTIONAL Accessibility Service to help automate sending photos via WhatsApp. This service must be manually enabled by the user in their phone's settings.

1.  Navigate to the in-app settings screen (this will be implemented later).
2.  There will be a button like "Enable Automated Sending". Tapping this will open the phone's main Accessibility settings screen.
3.  The user must find "Lunar Tag" in the list of downloaded apps and turn the service ON.
4.  Once enabled, when a scheduled send is triggered, the app will attempt to automatically find the correct WhatsApp group (based on the name in settings) and press the send button.
5.  If this automation fails, the user will simply be left on the WhatsApp share screen and must tap the send button themselves.
