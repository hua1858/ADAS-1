/*
 * Copyright (c) 2017 Ahmed-Abdelmeged
 *
 * github: https://github.com/Ahmed-Abdelmeged
 * email: ahmed.abdelmeged.vm@gamil.com
 * Facebook: https://www.facebook.com/ven.rto
 * Twitter: https://twitter.com/A_K_Abd_Elmeged
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.mego.adas.user_info;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.mego.adas.R;
import com.example.mego.adas.auth.AuthenticationUtilities;
import com.example.mego.adas.auth.User;
import com.example.mego.adas.utils.AdasUtils;
import com.example.mego.adas.utils.Constants;
import com.example.mego.adas.utils.NetworkUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.concurrent.ExecutionException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


/**
 * Activity to display and edit user info
 */
public class EditUserInfoActivity extends AppCompatActivity {

    private static final int RC_PHOTO_PICKER = 6934;

    /**
     * UI Element
     */
    // User item
    @BindView(R.id.edit_user_image)
    ImageView editUserImageImageView;

    @BindView(R.id.edit_user_email)
    TextView editUserEmailTextView;

    @BindView(R.id.edit_user_number)
    TextView editUserPhoneTextView;

    @BindView(R.id.edit_user_name)
    TextView editUserNameTextView;

    @BindView(R.id.edit_user_location)
    TextView editUserLocationTextView;

    @BindView(R.id.loading_image_progress_bar)
    ProgressBar imageUploadingProgressBar;

    @BindView(R.id.upload_progress_text)
    TextView uploadingProgressTextView;

    @BindView(R.id.edit_user_email_label)
    TextView editUserEmailLabelTextView;

    private ProgressDialog mProgressVerify;

    private Toast toast = null;

    /**
     * Variables to update progress
     */
    private int totalBytes = 0;
    private int bytesTransferred = 0;
    private double downloadingPercentage = 0;

    /**
     * Firebase objects
     */
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mUsersPhotosStorageReference;
    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mUsersImageDatabaseReference;
    private ValueEventListener mUserImageValueEventListener;

    /**
     * Firebase Authentication
     */
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseUser currentFirebaseUser;


    private String userImagePath = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.activity_edit_user_info);

        ButterKnife.bind(this);

        //initialize the Firebase auth object
        mFirebaseAuth = FirebaseAuth.getInstance();
        currentFirebaseUser = mFirebaseAuth.getCurrentUser();

    }

    @Override
    protected void onResume() {
        super.onResume();

        showCurrentUser();
        verifyEmail();

        //get current user
        User user = AuthenticationUtilities.getCurrentUser(this);

        //get instance for firebase objects
        mFirebaseStorage = FirebaseStorage.getInstance();
        mUsersPhotosStorageReference = mFirebaseStorage.getReference().child(Constants.FIREBASE_USER_IMAGE)
                .child(user.getUserUid());

        //set up the firebase
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mUsersImageDatabaseReference = mFirebaseDatabase.getReference().child(Constants.FIREBASE_USERS)
                .child(user.getUserUid()).child(Constants.FIREBASE_USER_IMAGE);


        //display current user image
        Bitmap userImageBitmap = AdasUtils.loadUserImageFromStorage(
                AdasUtils.getCurrentUserImagePath(EditUserInfoActivity.this));
        if (userImageBitmap != null) {
            editUserImageImageView.setImageBitmap(userImageBitmap);
        } else {
            editUserImageImageView.setImageResource(R.drawable.app_logo);
        }

        if (AdasUtils.getCurrentUserImagePath(EditUserInfoActivity.this) == null) {
            getUserImageUrl();
        }

        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            //get the choose photo as URI
            final Uri selectedImageUri = data.getData();

            //get reference to store user photo
            StorageReference photoRef =
                    mUsersPhotosStorageReference.child(selectedImageUri.getLastPathSegment());

            userImagePath = selectedImageUri.getLastPathSegment();

            photoRef.putFile(selectedImageUri).addOnSuccessListener(taskSnapshot -> {

                //get the download url and display it
                Uri downloadUrl = taskSnapshot.getDownloadUrl();

                mUsersImageDatabaseReference.setValue(downloadUrl.toString());

                //get the user bitmap and save it in internal storage to offline access
                //and save bandwidth
                new DownloadUserImageBitmap().execute(downloadUrl.toString());

                imageUploadingProgressBar.setVisibility(View.INVISIBLE);
                uploadingProgressTextView.setVisibility(View.INVISIBLE);

            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    imageUploadingProgressBar.setVisibility(View.INVISIBLE);
                    uploadingProgressTextView.setVisibility(View.INVISIBLE);
                    showToast(getString(R.string.failed_to_upload_photo_try_again));
                }
            }).addOnProgressListener(taskSnapshot -> {
                imageUploadingProgressBar.setVisibility(View.VISIBLE);
                uploadingProgressTextView.setVisibility(View.VISIBLE);

                //get the progress states and show it
                totalBytes = (int) taskSnapshot.getTotalByteCount();
                bytesTransferred = (int) taskSnapshot.getBytesTransferred();

                downloadingPercentage = ((double) bytesTransferred / (double) totalBytes) * 100;

                uploadingProgressTextView.setText((int) downloadingPercentage + "%");

                imageUploadingProgressBar.setMax(totalBytes);
                imageUploadingProgressBar.setProgress(bytesTransferred);
            });

        }
    }

    @OnClick(R.id.edit_email_container)
    public void emailPressed() {
        if (currentFirebaseUser != null) {
            if (currentFirebaseUser.isEmailVerified()) {
                showToast(getString(R.string.edit_email_not_change));
            } else {
                sendVerificationEmail();
            }
        }
    }

    @OnClick(R.id.edit_name_container)
    public void namePressed() {
        Intent editUserNameIntent = new Intent(EditUserInfoActivity.this, EditUserNameActivity.class);
        startActivity(editUserNameIntent);
        overridePendingTransition(R.anim.enter_edit_activity, R.anim.exit_edit_activity);
    }

    @OnClick(R.id.edit_password_container)
    public void passwordPressed() {
        Intent editUserPasswordIntent = new Intent(EditUserInfoActivity.this, EditUserPasswordActivity.class);
        startActivity(editUserPasswordIntent);
        overridePendingTransition(R.anim.enter_edit_activity, R.anim.exit_edit_activity);
    }

    @OnClick(R.id.edit_phone_container)
    public void phonePressed() {
        Intent editUserPhoneIntent = new Intent(EditUserInfoActivity.this, EditUserPhoneActivity.class);
        startActivity(editUserPhoneIntent);
        overridePendingTransition(R.anim.enter_edit_activity, R.anim.exit_edit_activity);
    }

    @OnClick(R.id.edit_location_container)
    public void locationPressed() {
        Intent editUserLocationIntent = new Intent(EditUserInfoActivity.this, EditUserLocationActivity.class);
        startActivity(editUserLocationIntent);
        overridePendingTransition(R.anim.enter_edit_activity, R.anim.exit_edit_activity);
    }

    @OnClick(R.id.edit_user_image)
    public void imagePressed() {
        if (NetworkUtil.isAvailableInternetConnection(EditUserInfoActivity.this)) {
            Intent getImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
            getImageIntent.setType("image/*");
            getImageIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(Intent.createChooser(getImageIntent, "Complete action using"), RC_PHOTO_PICKER);
        } else {
            showToast(getString(R.string.no_internet_connection));
        }
    }

    /**
     * Method to set the current user information
     */
    private void showCurrentUser() {
        User currentUser = AuthenticationUtilities.getCurrentUser(EditUserInfoActivity.this);
        if (currentUser.getEmail() != null) {
            editUserEmailTextView.setText(currentUser.getEmail());
        }

        if (currentUser.getFullName() != null) {
            editUserNameTextView.setText(currentUser.getFullName());
        }

        if (currentUser.getPhoneNumber() != null) {
            editUserPhoneTextView.setText(currentUser.getPhoneNumber());
        }

        if (currentUser.getLocation() != null) {
            editUserLocationTextView.setText(currentUser.getLocation());
        }
    }

    /**
     * Method to send verification email
     */
    private void sendVerificationEmail() {
        if (NetworkUtil.isAvailableInternetConnection(EditUserInfoActivity.this)) {
            if (currentFirebaseUser != null) {
                showVerifyProgressDialog(getString(R.string.sending_verification_email));
                currentFirebaseUser.sendEmailVerification().addOnSuccessListener(aVoid -> {
                    hideVerifyProgressDialog();
                    showInfoDialog(getString(R.string.verification_is_sent));
                }).addOnFailureListener(e -> {
                    hideVerifyProgressDialog();
                    showInfoDialog(e.getLocalizedMessage());
                });
            }
        } else {
            showToast(getString(R.string.error_message_failed_sign_in_no_network));
        }

    }

    /**
     * Method to check if the email is verified
     */
    private void verifyEmail() {
        mAuthStateListener = firebaseAuth -> {
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser != null) {
                if (!currentUser.isEmailVerified()) {
                    editUserEmailLabelTextView.setText(getString(R.string.email_not_verified));
                    editUserEmailLabelTextView.setTextColor(getResources().getColor(R.color.red));
                } else {
                    editUserEmailLabelTextView.setText(getString(R.string.email));
                    editUserEmailLabelTextView.setTextColor(getResources().getColor(R.color.colorPrimary));
                }
            }
        };
    }

    @Override
    protected void onPause() {
        super.onPause();
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }

    /**
     * Fast way to call Toast
     */
    private void showToast(String message) {
        if (toast != null) {
            toast.cancel();
        }
        toast = Toast.makeText(EditUserInfoActivity.this, message, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * AsyncTask to download image
     */
    private class DownloadUserImageBitmap extends AsyncTask<String, Void, Bitmap> {
        Bitmap bitmap;

        @Override
        protected Bitmap doInBackground(String... params) {
            try {
                bitmap = Glide.with(EditUserInfoActivity.this)
                        .load(params[0])
                        .asBitmap()
                        .into(-1, -1)
                        .get();

                if (bitmap != null) {
                    //save the current image
                    String savedPath = AdasUtils.saveImageIntoInternalStorage(bitmap,
                            EditUserInfoActivity.this, userImagePath);

                    AdasUtils.setCurrentUserImagePath(EditUserInfoActivity.this
                            , savedPath + "/" + userImagePath);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            //display the image after save it
            editUserImageImageView.setImageBitmap(bitmap);
        }
    }

    /**
     * Method to get the user image url
     */
    private void getUserImageUrl() {
        mUserImageValueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String userImageUrl = dataSnapshot.getValue(String.class);
                    if (userImageUrl != null) {
                        for (int i = 0; i < userImageUrl.length(); i++) {
                            if (userImageUrl.charAt(i) == '?') {
                                userImagePath = userImageUrl.substring(i - 6, i);
                            }
                        }
                        new DownloadUserImageBitmap().execute(userImageUrl);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mUsersImageDatabaseReference.addValueEventListener(mUserImageValueEventListener);
    }

    /**
     * Helper method to show progress dialog
     */
    public void showVerifyProgressDialog(String message) {

        if (mProgressVerify == null) {
            mProgressVerify = new ProgressDialog(this);
            mProgressVerify.setMessage(message);
            mProgressVerify.setIndeterminate(true);
        }
        mProgressVerify.show();
    }

    /**
     * Helper method to hide progress dialog
     */
    public void hideVerifyProgressDialog() {
        if (mProgressVerify != null && mProgressVerify.isShowing()) {
            mProgressVerify.dismiss();
        }
    }

    /**
     * show a dialog that till that an error
     */
    private void showInfoDialog(String info) {
        // Create an AlertDialog.Builder and set the message, and click listeners
        // for the positive buttons on the dialog.
        AlertDialog.Builder builder = new AlertDialog.Builder(EditUserInfoActivity.this);
        builder.setMessage(info);

        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        //create and show the alert dialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        hideVerifyProgressDialog();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toast != null) {
            toast.cancel();
        }
    }
}
