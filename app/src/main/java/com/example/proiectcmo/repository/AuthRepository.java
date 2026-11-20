package com.example.proiectcmo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.proiectcmo.R;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private static final String TAG = "AuthRepository";
    private static final String USERS_COLLECTION = "users";

    public interface Callback {
        void onSuccess();

        void onError(@StringRes int messageRes);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void login(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    public void register(String name, String email, String password, Callback callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(R.string.error_generic);
                        return;
                    }
                    saveProfile(user, name);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    public void loginWithGoogle(String idToken, Callback callback) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        saveGoogleProfile(user);
                    }
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    public void sendPasswordReset(String email, Callback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    public void logout() {
        auth.signOut();
    }

    private void saveProfile(@NonNull FirebaseUser user, String name) {
        UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        user.updateProfile(profile)
                .addOnFailureListener(e -> Log.w(TAG, "Nu s-a putut seta numele", e));

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("email", user.getEmail());
        data.put("createdAt", FieldValue.serverTimestamp());
        firestore.collection(USERS_COLLECTION).document(user.getUid())
                .set(data)
                .addOnFailureListener(e -> Log.w(TAG, "Nu s-a putut salva profilul", e));
    }

    private void saveGoogleProfile(@NonNull FirebaseUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.getDisplayName());
        data.put("email", user.getEmail());
        firestore.collection(USERS_COLLECTION).document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Nu s-a putut salva profilul", e));
    }

    @StringRes
    private static int mapError(Exception e) {
        Log.w(TAG, "Eroare Firebase", e);

        if (e instanceof FirebaseAuthWeakPasswordException) {
            return R.string.error_password_short;
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return R.string.error_email_in_use;
        } else if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return R.string.error_invalid_credentials;
        } else if (e instanceof FirebaseNetworkException) {
            return R.string.error_network;
        } else if (e instanceof FirebaseTooManyRequestsException) {
            return R.string.error_too_many_requests;
        }
        return R.string.error_generic;
    }
}
