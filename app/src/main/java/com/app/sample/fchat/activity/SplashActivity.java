package com.app.sample.fchat.activity;

import android.content.Intent;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.app.sample.fchat.R;
import com.app.sample.fchat.data.SettingsAPI;
import com.app.sample.fchat.ui.CustomToast;
import com.app.sample.fchat.util.Constants;
import com.app.sample.fchat.util.Tools;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import static com.app.sample.fchat.activity.SelectFriendActivity.USERS_CHILD;
import static com.app.sample.fchat.util.Constants.NODE_NAME;
import static com.app.sample.fchat.util.Constants.NODE_PHOTO;
import static com.app.sample.fchat.util.Constants.NODE_USER_ID;

public class SplashActivity extends AppCompatActivity {
    SignInButton button;
    private FirebaseAuth mAuth;
    public static final int RC_SIGN_IN = 1;
    private static final String TAG = "SplashActivity";
    GoogleSignInClient mGoogleSignInClient;
    SettingsAPI set;

    CustomToast customToast;
    private DatabaseReference ref;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        button = findViewById(R.id.sign_in_button);
        customToast = new CustomToast(this);
        mAuth = FirebaseAuth.getInstance();
        set  = new SettingsAPI(this);
        progressBar = findViewById(R.id.login_progress);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signIn();
                button.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.VISIBLE);
            }
        });

        if (getIntent().getStringExtra("mode") != null) {
            if (getIntent().getStringExtra("mode").equals("logout")) {
                set.deleteAllSettings();
                mAuth.signOut();
            }
        }


        //Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);


    }

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.getId());
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Fail", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            startActivity(new Intent(SplashActivity.this, MainActivity.class));


                            if (!task.isSuccessful()) {
                                customToast.showError(getString(R.string.error_authetication_failed));
                            } else {
                                ref = FirebaseDatabase.getInstance().getReference(USERS_CHILD);
                                ref.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(
                                            @NotNull
                                                    DataSnapshot snapshot) {
                                        final String usrNm = acct.getDisplayName();
                                        final String usrId = acct.getId();
                                        final String usrDp = acct.getPhotoUrl().toString();
                                        Log.d(TAG, "onDataChange:  username is"+usrNm);
                                        set.addUpdateSettings(Constants.PREF_MY_ID, usrId);
                                        set.addUpdateSettings(Constants.PREF_MY_NAME, usrNm);
                                        set.addUpdateSettings(Constants.PREF_MY_DP, usrDp);

                                        if (!snapshot.hasChild(usrId)) {
                                            ref.child(usrId + "/" + NODE_NAME).setValue(usrNm);
                                            ref.child(usrId + "/" + NODE_PHOTO).setValue(usrDp);
                                            ref.child(usrId + "/" + NODE_USER_ID).setValue(usrId);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        Log.d(TAG, "onCancelled: " + databaseError.getMessage());
                                    }
                                });

                            }

                        }
                    }

                });
    }


    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }
    }


}
