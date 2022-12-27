package com.example.bookapp.activities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Adapter;
import android.widget.Toast;

import com.example.bookapp.MyApplication;
import com.example.bookapp.R;
import com.example.bookapp.adapters.AdapterComment;
import com.example.bookapp.adapters.AdapterPdfFavourite;
import com.example.bookapp.databinding.ActivityPdfDetailBinding;
import com.example.bookapp.databinding.DialogCommentAddBinding;
import com.example.bookapp.models.ModelComment;
import com.example.bookapp.models.ModelPdf;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;

public class PdfDetailActivity extends AppCompatActivity {

    private static final String TAG_DOWNLOAD = "DOWNLOAD_TAG";

    private ActivityPdfDetailBinding binding;

    String bookId, bookTitle, bookUrl;

    boolean isInMyFavourite = false;

    private FirebaseAuth firebaseAuth;

    private ProgressDialog progressDialog;

    private ArrayList<ModelComment> commentArrayList;
    private AdapterComment adapterComment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPdfDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        Intent intent = getIntent();
        bookId = intent.getStringExtra("bookId");

        binding.dowloadBookBtn.setVisibility(View.GONE);

        progressDialog = new ProgressDialog(this);

        progressDialog.setTitle("Пожалуйста, подождите");
        progressDialog.setCanceledOnTouchOutside(false);


        firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser()!= null){
            checkIsFavourite();
        }

        loadBookDetails();
        loadComments();
        MyApplication.incrementBookViewCount(bookId);



        binding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        binding.readBookBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent1 = new Intent(PdfDetailActivity.this, PdfViewActivity.class);
                intent1.putExtra("bookId", bookId);
                startActivity(intent1);
            }
        });

        binding.dowloadBookBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG_DOWNLOAD, "onClick: Checking permission");
                if (ContextCompat.checkSelfPermission(PdfDetailActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
                    Log.d(TAG_DOWNLOAD, "onClick: Permission already granted, can download book");
                    MyApplication.downloadBook(PdfDetailActivity.this, ""+bookId, ""+bookTitle, ""+bookUrl);
                }
                else{
                    Log.d(TAG_DOWNLOAD, "onClick: permission was not granted, request permission");
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);

                }
            }
        });

        binding.favouriteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (firebaseAuth.getCurrentUser() == null){
                    Toast.makeText(PdfDetailActivity.this, "Вы не авторизованы", Toast.LENGTH_SHORT).show();
                }
                else{
                    if(isInMyFavourite){
                        MyApplication.removeFromFavourite(PdfDetailActivity.this, bookId);

                    }
                    else{
                        MyApplication.addToFavourite(PdfDetailActivity.this, bookId);
                    }
                }
            }
        });

        binding.addCommentBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (firebaseAuth.getCurrentUser() == null){
                    Toast.makeText(PdfDetailActivity.this, "Вы не авторизованы.", Toast.LENGTH_SHORT).show();
                }
                else{
                    addCommentDialog();
                }
            }
        });
    }

    private void loadComments() {
        commentArrayList = new ArrayList<>();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
        ref.child(bookId).child("Comments").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                commentArrayList.clear();
                for(DataSnapshot ds: snapshot.getChildren()){
                    ModelComment model = ds.getValue(ModelComment.class);
                    commentArrayList.add(model);

                }
                adapterComment = new AdapterComment(PdfDetailActivity.this, commentArrayList);
                binding.commentRv.setAdapter(adapterComment);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

    }

    private String comment = "";

    private void addCommentDialog() {
        DialogCommentAddBinding commentAddBinding = DialogCommentAddBinding.inflate(LayoutInflater.from(this));

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialog);
        builder.setView(commentAddBinding.getRoot());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        commentAddBinding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                alertDialog.dismiss();
            }
        });

        commentAddBinding.submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                comment = commentAddBinding.commentEt.getText().toString().trim();
                if(TextUtils.isEmpty(comment)){
                    Toast.makeText(PdfDetailActivity.this, "Введите комментарий", Toast.LENGTH_SHORT).show();

                }
                else{
                    alertDialog.dismiss();
                    addComment();
                }
            }
        });
    }

    private void addComment() {
        progressDialog.setMessage("Комментарий добавляется...");
        progressDialog.show();

        String timestamp = ""+System.currentTimeMillis();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("id", ""+timestamp);
        hashMap.put("comment", ""+comment);
        hashMap.put("bookId", ""+bookId);
        hashMap.put("timestamp", ""+timestamp);
        hashMap.put("uid", ""+firebaseAuth.getUid());

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
        ref.child(bookId).child("Comments").child(timestamp).setValue(hashMap).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Toast.makeText(PdfDetailActivity.this, "Комментарий добавлен.", Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                progressDialog.dismiss();
                Toast.makeText(PdfDetailActivity.this, "Не удалось добавить комментарий "+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


   }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
               if (isGranted){
                   Log.d(TAG_DOWNLOAD, ": Permission granted");
                   MyApplication.downloadBook(this, ""+bookId, ""+bookTitle, ""+bookUrl);
               }
               else {
                   Log.d(TAG_DOWNLOAD, "Permission was denied...: ");
                   Toast.makeText(this, "Разрешение отклонено", Toast.LENGTH_SHORT).show();
               }
            });

    private void loadBookDetails() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
        ref.child(bookId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                bookTitle = ""+snapshot.child("title").getValue();
                String description = ""+snapshot.child("description").getValue();
                String categoryId = ""+snapshot.child("categoryId").getValue();
                String viewsCount = ""+snapshot.child("viewsCount").getValue();
                String downloadsCount = ""+snapshot.child("downloadsCount").getValue();
                bookUrl = ""+snapshot.child("url").getValue();
                String timestamp = ""+snapshot.child("timestamp").getValue();

                binding.dowloadBookBtn.setVisibility(View.VISIBLE);

                String date = MyApplication.formatTimestamp(Long.parseLong(timestamp));

                MyApplication.loadCategory(""+categoryId, binding.categoryTv);
                MyApplication.loadPdfFromUrlSinglePage(""+bookUrl, ""+bookTitle, binding.pdfView, binding.progressBar, binding.pagesTv);

                MyApplication.loadPdfSize(""+bookUrl,""+bookTitle, binding.sizeTv);

                binding.titleTv.setText(bookTitle);
                binding.descriptionTv.setText(description);
                binding.viewsTv.setText(viewsCount.replace("null", "N/A"));
                binding.downloadsTv.setText(downloadsCount.replace("null", "N/A"));
                binding.dateTv.setText(date);


            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void checkIsFavourite(){
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Users");
        reference.child(firebaseAuth.getUid()).child("Favourites").child(bookId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                isInMyFavourite = snapshot.exists();
                if (isInMyFavourite){
                    binding.favouriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_favorite_white, 0,0);
                    binding.favouriteBtn.setText("Убрать из избранного");
                }
                else{
                    binding.favouriteBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_favorite_border_white, 0,0);
                    binding.favouriteBtn.setText("Добавить в избранное");

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
}