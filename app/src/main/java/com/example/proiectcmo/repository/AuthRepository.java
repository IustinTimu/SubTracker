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

/**
 * Singurul loc din aplicație care comunică cu Firebase Authentication.
 * Activitățile nu folosesc Firebase direct, ci trec prin ViewModel → Repository
 * (separarea logicii de interfață).
 */
public class AuthRepository {

    private static final String TAG = "AuthRepository";
    private static final String USERS_COLLECTION = "users";

    /** Rezultatul unei operații asincrone (login, înregistrare etc.). */
    public interface Callback {
        void onSuccess();

        /** @param messageRes id-ul mesajului de eroare din strings.xml */
        void onError(@StringRes int messageRes);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    /** @return utilizatorul autentificat sau null dacă nu e nimeni logat. */
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /** Autentificare cu email și parolă. */
    public void login(String email, String password, Callback callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    /**
     * Creează un cont nou, îi setează numele afișat și salvează profilul
     * utilizatorului în Firestore (users/{uid}).
     */
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

    /**
     * Autentificare cu un cont Google.
     * Primește token-ul obținut de Credential Manager și îl schimbă pe o sesiune Firebase.
     * La prima autentificare, Firebase creează automat contul.
     */
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

    /** Trimite pe email un link pentru resetarea parolei. */
    public void sendPasswordReset(String email, Callback callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(mapError(e)));
    }

    /** Deconectează utilizatorul curent. */
    public void logout() {
        auth.signOut();
    }

    /**
     * Salvează numele în profilul Firebase și un document cu datele utilizatorului în Firestore.
     * Dacă una dintre operații eșuează, contul rămâne creat; eroarea doar se scrie în Logcat.
     */
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

    /**
     * Salvează în Firestore numele și emailul venite din contul Google.
     * merge() păstrează câmpurile existente (ex. createdAt) la autentificările următoare.
     */
    private void saveGoogleProfile(@NonNull FirebaseUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", user.getDisplayName());
        data.put("email", user.getEmail());
        firestore.collection(USERS_COLLECTION).document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Nu s-a putut salva profilul", e));
    }

    /** Transformă excepțiile Firebase în mesaje de eroare clare, în limba română. */
    @StringRes
    private static int mapError(Exception e) {
        Log.w(TAG, "Eroare Firebase", e);
        // Ordinea contează: WeakPassword este o subclasă a InvalidCredentials.
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
