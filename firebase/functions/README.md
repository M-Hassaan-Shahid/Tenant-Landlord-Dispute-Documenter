# ProofNest Cloud Functions

Sends FCM push notifications when a document is created in the `notifications` collection.

## Deploy

```bash
npm install --prefix firebase/functions
firebase deploy --only functions
```

Requires the [Firebase CLI](https://firebase.google.com/docs/cli) and a project selected (`firebase use <project-id>`).
