import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main {
    JFrame frame;
    DefaultListModel<String> listModel = new DefaultListModel<>();
    DefaultListModel<String> yearModel = new DefaultListModel<>();
    JList<String> recordList;
    JList<String> yearList;
    JTextArea recordDetail;
    java.util.List<Record> records = new ArrayList<>();
    java.util.List<Record> visibleRecords = new ArrayList<>();
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    java.util.List<Marker> markers = new ArrayList<>();
    Map<String, JCheckBox> kindChecksMap = new HashMap<>();
    Map<String, JButton> kindFilterMap = new HashMap<>();
    Map<String, RestaurantPreset> restaurantProfiles = new HashMap<>();
    String selectedYear = "\uC804\uCCB4"; // "전체"
    JPanel mapPanel;
    JLabel mapLabel;
    JTabbedPane tabbedPane;

    public Main(){
        ensureRecordsDir();
        loadRestaurantRegistry();
        frame = new JFrame("\uB9DB\uC9D1 \uCD94\uC5B5 \uC9C0\uB3C4"); // "맛집 추억 지도"
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200,860);
        Container c = frame.getContentPane();
        c.setLayout(new BorderLayout());

        // Create tabbed pane for Map and Records
        tabbedPane = new JTabbedPane();

        // ===== TAB 1: MAP VIEW =====
        JPanel mapViewPanel = createMapViewPanel();
        tabbedPane.addTab("\uC9C0\uB3C4", mapViewPanel); // "지도"

        // ===== TAB 2: RECORDS VIEW =====
        JPanel recordsViewPanel = createRecordsViewPanel();
        tabbedPane.addTab("\uAE30\uB85D", recordsViewPanel); // "기록"

        c.add(tabbedPane, BorderLayout.CENTER);

        reloadRecords();
        seedRestaurantProfilesFromRecords();
        if(yearModel.getSize() > 0) {
            yearList.setSelectedIndex(0);
        }

        frame.setResizable(false);
        frame.setVisible(true);
    }

    JPanel createMapViewPanel(){
        JPanel panel = new JPanel(new BorderLayout());

        // Left: map panel
        mapPanel = new JPanel(null);
        mapPanel.setPreferredSize(new Dimension(1000,800));
        ImageIcon mapIcon = new ImageIcon("images/\uC9C0\uB3C4.jpg"); // "images/지도.jpg"
        mapLabel = new JLabel(mapIcon);
        mapLabel.setBounds(0,0,1000,800);
        mapPanel.add(mapLabel);

        // Right: filter controls
        JPanel rightPanel = new JPanel();
        rightPanel.setPreferredSize(new Dimension(200,800));
        rightPanel.setMinimumSize(new Dimension(200,800));
        rightPanel.setMaximumSize(new Dimension(200,800));
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JButton btnAdd = new JButton("\uAE30\uB85D \uCD94\uAC00"); // "기록 추가"
        btnAdd.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnAdd.addActionListener(e -> new AddRecordDialog(frame, mapLabel, tabbedPane));
        rightPanel.add(btnAdd);
        rightPanel.add(Box.createVerticalStrut(10));

        // Filters
        rightPanel.add(new JLabel("\uCE74\uD14C\uACE0\uB9AC \uD544\uD130 (\uD1A0\uAE00):")); // "카테고리 필터 (토글):"
        String[] kinds = {"\uD55C\uC2DD","\uBD84\uC2DD","\uC911\uC2DD","\uC77C\uC2DD","\uC591\uC2DD","\uCE74\uD398","\uD328\uC2A4\uD2B8\uD478\uB4DC","\uAE30\uD0C0"};
        String[] uncheckedKinds = {"images/\uD55C\uC2DDunchecked.jpg","images/\uBD84\uC2DDunchecked.jpg","images/\uC911\uC2DDunchecked.jpg",
                "images/\uC77C\uC2DDunchecked.jpg","images/\uC591\uC2DDunchecked.jpg","images/\uCE74\uD398unchecked.jpg",
                "images/\uD328\uC2A4\uD2B8\uD478\uB4DCunchecked.jpg","images/\uAE30\uD0C0unchecked.jpg"};
        String[] checkedKinds = {"images/\uD55C\uC2DDchecked.jpg","images/\uBD84\uC2DDchecked.jpg","images/\uC911\uC2DDchecked.jpg",
                "images/\uC77C\uC2DDchecked.jpg","images/\uC591\uC2DDchecked.jpg","images/\uCE74\uD398checked.jpg",
                "images/\uD328\uC2A4\uD2B8\uD478\uB4DCchecked.jpg","images/\uAE30\uD0C0checked.jpg"};

        for(int i = 0; i < kinds.length; i++){
            final int idx = i;
            JButton filterBtn = new JButton();
            filterBtn.setPreferredSize(new Dimension(170, 60));
            filterBtn.setMaximumSize(new Dimension(170, 60));
            filterBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

            ImageIcon checkedIcon = new ImageIcon(checkedKinds[i]);
            ImageIcon uncheckedIcon = new ImageIcon(uncheckedKinds[i]);

            filterBtn.setIcon(checkedIcon);
            filterBtn.setBorderPainted(false);
            filterBtn.setContentAreaFilled(false);
            filterBtn.setFocusPainted(false);

            filterBtn.putClientProperty("checked", true);
            filterBtn.putClientProperty("checkedIcon", checkedIcon);
            filterBtn.putClientProperty("uncheckedIcon", uncheckedIcon);

            filterBtn.addActionListener(e -> {
                boolean isChecked = (Boolean)filterBtn.getClientProperty("checked");
                isChecked = !isChecked;
                filterBtn.putClientProperty("checked", isChecked);
                if(isChecked) {
                    filterBtn.setIcon((ImageIcon)filterBtn.getClientProperty("checkedIcon"));
                } else {
                    filterBtn.setIcon((ImageIcon)filterBtn.getClientProperty("uncheckedIcon"));
                }
                reloadRecords();
                updateMarkerVisibility();
            });

            rightPanel.add(filterBtn);
            kindFilterMap.put(kinds[i], filterBtn);
            kindChecksMap.put(kinds[i], new JCheckBox(kinds[i], true));
        }

        // After building filter buttons, load markers so filters exist
        loadMarkersFromRecords();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, rightPanel);
        split.setDividerLocation(1000);
        // Prevent user from resizing the divider so right panel stays fixed
        split.setEnabled(false);
        split.setDividerSize(0);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    JPanel createRecordsViewPanel(){
        JPanel panel = new JPanel(new BorderLayout());

        JPanel yearPanel = new JPanel(new BorderLayout());
        yearPanel.add(new JLabel("\uB144\uB3C4\uBCC4 \uBCF4\uAE30"), BorderLayout.NORTH); // "년도별 보기"
        yearList = new JList<>(yearModel);
        yearList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        yearList.addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting()) {
                String value = yearList.getSelectedValue();
                if(value != null) {
                    selectedYear = value;
                    applyYearFilter();
                }
            }
        });
        yearPanel.add(new JScrollPane(yearList), BorderLayout.CENTER);
        panel.add(yearPanel, BorderLayout.NORTH);

        recordList = new JList<>(listModel);
        recordList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recordList.addListSelectionListener(e -> showSelectedRecord());
        // 더블클릭시 기록 보기 다이얼로그 표시
        recordList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(e.getClickCount() == 2) {
                    int idx = recordList.getSelectedIndex();
                    if(idx >= 0 && idx < visibleRecords.size()) {
                        Record r = visibleRecords.get(idx);
                        openRestaurantRecordList(r.restaurantName, r.date);
                    }
                }
            }
        });
        panel.add(new JScrollPane(recordList), BorderLayout.CENTER);

        recordDetail = new JTextArea();
        recordDetail.setEditable(false);
        recordDetail.setLineWrap(true);
        recordDetail.setWrapStyleWord(true);
        recordDetail.setPreferredSize(new Dimension(1000,200));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JScrollPane(recordDetail), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton showMapBtn = new JButton("\uC704\uCE58 \uBCF4\uAE30"); // "위치 보기"
        showMapBtn.addActionListener(e -> showLocationOnMap());
        buttonPanel.add(showMapBtn);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    void ensureRecordsDir(){
        File d = new File("Records");
        if(!d.exists()) d.mkdir();
    }

    void reloadRecords(){
        records.clear();
        yearModel.clear();
        File dir = new File("Records");
        File[] files = dir.listFiles((d,name)->name.toLowerCase().endsWith(".txt"));
        if(files==null) return;
        for(File f: files){
            String fname = f.getName();
            // expected format: YYYY-MM-DD_식당명.txt
            String base = fname.substring(0, fname.length()-4);
            String[] parts = base.split("_",2);
            LocalDate date = null;
            String rname = base;
            try{
                date = LocalDate.parse(parts[0], dtf);
                if(parts.length>1) rname = parts[1];
            }catch(Exception ex){
                // fallback: treat as no-date
                rname = base;
            }
            // Prefer reading the first line of the file to get the real 식당명
            try(BufferedReader br = Files.newBufferedReader(f.toPath(), Charset.forName("MS949"))){
                String first = br.readLine();
                if(first!=null && first.startsWith("\uC2DD\uB2F9\uBA85:")){ // "식당명:"
                    String real = first.substring(4).trim();
                    if(!real.isEmpty()) rname = real;
                }
            }catch(Exception ignored){ }
            records.add(new Record(f.getAbsolutePath(), date, rname));
        }
        // sort by date desc (nulls last)
        records.sort((a,b)->{
            if(a.date==null && b.date==null) return a.path.compareTo(b.path);
            if(a.date==null) return 1;
            if(b.date==null) return -1;
            return b.date.compareTo(a.date);
        });
        LinkedHashSet<String> years = new LinkedHashSet<>();
        years.add("\uC804\uCCB4"); // "전체"
        for(Record r: records){
            if(r.date != null) years.add(String.valueOf(r.date.getYear()));
        }
        for(String y: years) yearModel.addElement(y);
        if(selectedYear == null || !years.contains(selectedYear)) selectedYear = "\uC804\uCCB4"; // "전체"
        if(yearList != null && yearModel.getSize() > 0) {
            yearList.setSelectedValue(selectedYear, true);
        }
        applyYearFilter();
    }

    void loadRestaurantRegistry(){
        restaurantProfiles.clear();
        File registryFile = new File("RestaurantRegistry.txt");
        if(!registryFile.exists()) return;

        try(BufferedReader br = Files.newBufferedReader(registryFile.toPath(), Charset.forName("MS949"))){
            String line;
            while((line = br.readLine()) != null){
                String[] parts = line.split("\\|", -1);
                if(parts.length < 4) continue;
                String name = parts[0].trim();
                String category = parts[1].trim();
                int x = Integer.parseInt(parts[2].trim());
                int y = Integer.parseInt(parts[3].trim());
                if(!name.isEmpty()){
                    restaurantProfiles.put(name, new RestaurantPreset(category, x, y));
                }
            }
        }catch(Exception ignored){ }
    }

    void seedRestaurantProfilesFromRecords(){
        for(Record record : records){
            if(restaurantProfiles.containsKey(record.restaurantName)) continue;
            RestaurantPreset preset = readPresetFromRecordFile(record.path);
            if(preset != null){
                restaurantProfiles.put(record.restaurantName, preset);
            }
        }
    }

    RestaurantPreset readPresetFromRecordFile(String path){
        try(BufferedReader br = Files.newBufferedReader(new File(path).toPath(), Charset.forName("MS949"))){
            String line;
            String category = "\uAE30\uD0C0"; // "기타"
            int x = -1;
            int y = -1;

            while((line = br.readLine()) != null){
                if(line.startsWith("\uCE74\uD14C\uACE0\uB9AC:")) category = line.substring(5).trim(); // "카테고리:"
                if(line.startsWith("\uC704\uCE58X:")) x = Integer.parseInt(line.substring(4).trim()); // "위치X:"
                if(line.startsWith("\uC704\uCE58Y:")) y = Integer.parseInt(line.substring(4).trim()); // "위치Y:"
            }

            if(x >= 0 && y >= 0){
                return new RestaurantPreset(category, x, y);
            }
        }catch(Exception ignored){ }
        return null;
    }

    void saveRestaurantRegistryEntry(String restaurantName, String category, int x, int y){
        if(restaurantName == null || restaurantName.trim().isEmpty()) return;
        restaurantProfiles.put(restaurantName, new RestaurantPreset(category, x, y));

        Map<String, RestaurantPreset> merged = new LinkedHashMap<>(restaurantProfiles);
        try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("RestaurantRegistry.txt"), Charset.forName("MS949")))){
            for(Map.Entry<String, RestaurantPreset> entry : merged.entrySet()){
                RestaurantPreset preset = entry.getValue();
                bw.write(entry.getKey() + "|" + preset.category + "|" + preset.x + "|" + preset.y + "\n");
            }
        }catch(Exception ignored){ }
    }

    void applyYearFilter(){
        listModel.clear();
        visibleRecords.clear();
        for(Record r: records){
            if("\uC804\uCCB4".equals(selectedYear) || (r.date != null && String.valueOf(r.date.getYear()).equals(selectedYear))){ // "전체"
                visibleRecords.add(r);
                String label = (r.date!=null? r.date.format(dtf)+"_":"")+r.restaurantName;
                listModel.addElement(label);
            }
        }
        if(!visibleRecords.isEmpty()){
            recordList.setSelectedIndex(0);
        } else {
            recordDetail.setText("");
        }
    }

    void showSelectedRecord(){
        int idx = recordList.getSelectedIndex();
        if(idx<0) return;
        if(idx >= visibleRecords.size()) return;
        Record r = visibleRecords.get(idx);
        try(BufferedReader br = Files.newBufferedReader(new File(r.path).toPath(), Charset.forName("MS949"))){
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null){
                sb.append(line).append("\n");
            }
            recordDetail.setText(sb.toString());
        }catch(Exception e){
            recordDetail.setText("\uD30C\uC77C\uC744 \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4: "+e.getMessage()); // "파일을 읽을 수 없습니다: "
        }
    }

    void openRestaurantRecordList(String restaurantName){
        openRestaurantRecordList(restaurantName, null);
    }

    void openRestaurantRecordList(String restaurantName, LocalDate selectedDate){
        // find all records matching restaurantName
        java.util.List<Record> matchedRecords = new ArrayList<>();
        for(Record r: records){
            if(r.restaurantName.equals(restaurantName)){
                matchedRecords.add(r);
            }
        }

        if(matchedRecords.isEmpty()){
            JOptionPane.showMessageDialog(frame, "\uD574\uB2F9 \uC2DD\uB2F9\uC758 \uAE30\uB85D\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."); // "해당 식당의 기록이 없습니다."
            return;
        }

        // Create dialog to show all records
        JDialog dialog = new JDialog(frame, restaurantName + " - \uAE30\uB85D \uBCF4\uAE30", true); // " - 기록 보기"
        dialog.setSize(600, 450);
        dialog.setLayout(new BorderLayout(10, 10));

        // Top: buttons for each record
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        java.util.List<JButton> buttons = new ArrayList<>();

        // selectedDate와 일치하는 인덱스 찾기
        int selectedIdx = 0;
        for(int i = 0; i < matchedRecords.size(); i++){
            if(selectedDate != null && matchedRecords.get(i).date != null &&
                    matchedRecords.get(i).date.equals(selectedDate)){
                selectedIdx = i;
                break;
            }
        }

        for(int i = 0; i < matchedRecords.size(); i++){
            final int idx = i;
            // 날짜로 버튼 텍스트 설정
            String dateStr = matchedRecords.get(i).date != null ? matchedRecords.get(i).date.format(dtf) : "\uB0A0\uC9DC\uC5C6\uC74C"; // "날짜없음"
            JButton btn = new JButton(dateStr);
            btn.setPreferredSize(new Dimension(110, 40));
            btn.setFont(new Font("Arial", Font.BOLD, 11));
            btn.setForeground(Color.BLACK);
            btn.addActionListener(e -> displayRecord(dialog, matchedRecords, idx, buttons));
            buttonPanel.add(btn);
            buttons.add(btn);
            if(i == selectedIdx) {
                btn.setBackground(Color.YELLOW);
                btn.setOpaque(true);
            }
        }
        JScrollPane buttonScroll = new JScrollPane(buttonPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        buttonScroll.getHorizontalScrollBar().setUnitIncrement(20);
        buttonScroll.setPreferredSize(new Dimension(580, 80));
        dialog.add(buttonScroll, BorderLayout.NORTH);

        // Center: detail text area
        JTextArea detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(detailArea);
        dialog.add(sp, BorderLayout.CENTER);

        // Display selected record
        Record selectedRecord = matchedRecords.get(selectedIdx);
        try(BufferedReader br = Files.newBufferedReader(new File(selectedRecord.path).toPath(), Charset.forName("MS949"))){
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null){
                sb.append(line).append("\n");
            }
            detailArea.setText(sb.toString());
        }catch(Exception ex){
            detailArea.setText("\uD30C\uC77C\uC744 \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4: "+ex.getMessage()); // "파일을 읽을 수 없습니다: "
        }

        // Store detailArea and buttons in a way we can access it from button listeners
        dialog.getRootPane().putClientProperty("detailArea", detailArea);
        dialog.getRootPane().putClientProperty("buttons", buttons);

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    void displayRecord(JDialog dialog, java.util.List<Record> records, int idx, java.util.List<JButton> buttons){
        JTextArea detailArea = (JTextArea) dialog.getRootPane().getClientProperty("detailArea");
        Record r = records.get(idx);

        // 모든 버튼의 배경색 초기화하고 선택된 버튼만 노란색으로 설정
        for(int i = 0; i < buttons.size(); i++){
            if(i == idx){
                buttons.get(i).setBackground(Color.YELLOW);
                buttons.get(i).setOpaque(true);
            } else {
                buttons.get(i).setBackground(UIManager.getColor("Button.background"));
                buttons.get(i).setOpaque(false);
            }
        }

        try(BufferedReader br = Files.newBufferedReader(new File(r.path).toPath(), Charset.forName("MS949"))){
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null){
                sb.append(line).append("\n");
            }
            detailArea.setText(sb.toString());
        }catch(Exception ex){
            detailArea.setText("\uD30C\uC77C\uC744 \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4: "+ex.getMessage()); // "파일을 읽을 수 없습니다: "
        }
    }

    void showLocationOnMap(){
        int idx = recordList.getSelectedIndex();
        if(idx<0 || idx >= visibleRecords.size()) {
            JOptionPane.showMessageDialog(frame, "\uAE30\uB85D\uC744 \uC120\uD0DD\uD558\uC138\uC694"); // "기록을 선택하세요"
            return;
        }
        Record r = visibleRecords.get(idx);

        // Read location from file
        try{
            // Prefer registry entry for location
            RestaurantPreset preset = restaurantProfiles.get(r.restaurantName);
            int locX = -1, locY = -1;
            if(preset != null){
                locX = preset.x;
                locY = preset.y;
            } else {
                try(BufferedReader br = Files.newBufferedReader(new File(r.path).toPath(), Charset.forName("MS949"))){
                    String line;
                    while((line = br.readLine()) != null){
                        if(line.startsWith("\uC704\uCE58X:")) locX = Integer.parseInt(line.substring(4).trim()); // "위치X:"
                        if(line.startsWith("\uC704\uCE58Y:")) locY = Integer.parseInt(line.substring(4).trim()); // "위치Y:"
                    }
                }
            }

            if(locX < 0 || locY < 0){
                JOptionPane.showMessageDialog(frame, "\uC704\uCE58 \uC815\uBCF4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4"); // "위치 정보가 없습니다"
                return;
            }

            // Show location on map
            JDialog mapDialog = new JDialog(frame, r.restaurantName + " - \uC704\uCE58", true); // " - 위치"
            mapDialog.setSize(1000, 800);
            JPanel mapPanel = new JPanel(null);

            ImageIcon mapIcon = new ImageIcon("images/\uC9C0\uB3C4.jpg"); // "images/지도.jpg"
            JLabel mapLabel = new JLabel(mapIcon);
            mapLabel.setBounds(0,0,1000,800);
            mapPanel.add(mapLabel);

            // Mark location
            JButton marker = new JButton("●");
            marker.setFont(new Font("Arial", Font.PLAIN, 20));
            marker.setForeground(Color.RED);
            marker.setBounds(locX-5, locY-5, 20, 20);
            marker.setEnabled(false);
            mapPanel.add(marker);
            try{ mapPanel.setComponentZOrder(marker, 0); }catch(Exception ignore){}

            mapDialog.add(mapPanel);
            mapDialog.setLocationRelativeTo(frame);
            mapDialog.setVisible(true);

        }catch(Exception ex){
            JOptionPane.showMessageDialog(frame, "\uC704\uCE58 \uC815\uBCF4\uB97C \uC77D\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4: "+ex.getMessage()); // "위치 정보를 읽을 수 없습니다: "
        }
    }

    void openMostRecentForRestaurant(String restaurantName){
        // find most recent record matching restaurantName
        for(Record r: records){
            if(r.restaurantName.equals(restaurantName)){
                int idx = records.indexOf(r);
                recordList.setSelectedIndex(idx);
                recordList.ensureIndexIsVisible(idx);
                return;
            }
        }
        JOptionPane.showMessageDialog(frame, "\uD574\uB2F9 \uC2DD\uB2F9\uC758 \uAE30\uB85D\uC774 \uC5C6\uC2B5\uB2C8\uB2E4."); // "해당 식당의 기록이 없습니다."
    }

    class AddRecordDialog extends JDialog{
        JComboBox<String> restCombo;
        JComboBox<String> categoryCombo;
        JTextField menuField, priceField, dateField, locationField;
        JTextArea noteArea;
        int selectedX = -1, selectedY = -1;
        JLabel mapLabel;
        JTabbedPane tabbedPane;
        MouseListener activeLocationListener;

        public AddRecordDialog(JFrame owner, JLabel mapLabelRef, JTabbedPane tabbedPaneRef){
            super(owner, "\uAE30\uB85D \uCD94\uAC00", false); // "기록 추가"
            setSize(400,550);
            setLayout(null);
            this.mapLabel = mapLabelRef;
            this.tabbedPane = tabbedPaneRef;

            // Get existing restaurant names
            Set<String> restNames = new TreeSet<>();
            restNames.addAll(restaurantProfiles.keySet());
            for(Record r : records){
                restNames.add(r.restaurantName);
            }

            JLabel restLabel = new JLabel("\uC2DD\uB2F9\uBA85:"); // "식당명:"
            restLabel.setBounds(20, 20, 80, 20);
            add(restLabel);
            restCombo = new JComboBox<>(restNames.toArray(new String[0]));
            restCombo.setEditable(true);
            restCombo.setBounds(100, 20, 260, 25);
            add(restCombo);
            restCombo.addActionListener(e -> applyRestaurantDefaults());

            JLabel categoryLabel = new JLabel("\uCE74\uD14C\uACE0\uB9AC:"); // "카테고리:"
            categoryLabel.setBounds(20, 55, 80, 20);
            add(categoryLabel);
            String[] kinds = {"\uD55C\uC2DD","\uBD84\uC2DD","\uC911\uC2DD","\uC77C\uC2DD","\uC591\uC2DD","\uCE74\uD398","\uD328\uC2A4\uD2B8\uD478\uB4DC","\uAE30\uD0C0"};
            categoryCombo = new JComboBox<>(kinds);
            categoryCombo.setSelectedIndex(7); // Default to "기타"
            categoryCombo.setBounds(100, 55, 260, 25);
            add(categoryCombo);

            menuField = new JTextField();
            JLabel menuLabel = new JLabel("\uBA54\uB274:"); // "메뉴:"
            menuLabel.setBounds(20, 90, 80, 20);
            add(menuLabel);
            menuField.setBounds(100, 90, 260, 25);
            add(menuField);

            priceField = new JTextField();
            JLabel priceLabel = new JLabel("\uAC00\uACA9:"); // "가격:"
            priceLabel.setBounds(20, 125, 80, 20);
            add(priceLabel);
            priceField.setBounds(100, 125, 260, 25);
            add(priceField);

            dateField = new JTextField(LocalDate.now().format(dtf));
            JLabel dateLabel = new JLabel("\uB0A0\uC9DC:"); // "날짜:"
            dateLabel.setBounds(20, 160, 80, 20);
            add(dateLabel);
            dateField.setBounds(100, 160, 260, 25);
            add(dateField);

            locationField = new JTextField("\uD074\uB9AD\uD558\uC5EC \uC704\uCE58 \uC120\uD0DD"); // "클릭하여 위치 선택"
            locationField.setEditable(false);
            JLabel locLabel = new JLabel("\uC704\uCE58:"); // "위치:"
            locLabel.setBounds(20, 195, 80, 20);
            add(locLabel);
            locationField.setBounds(100, 195, 260, 25);
            add(locationField);

            JButton selectLocBtn = new JButton("\uC9C0\uB3C4\uC5D0\uC11C \uD074\uB9AD"); // "지도에서 클릭"
            selectLocBtn.setBounds(20, 230, 340, 30);
            add(selectLocBtn);
            selectLocBtn.addActionListener(e -> selectLocationOnMap());

            JLabel noteLabel = new JLabel("\uBA54\uBAA8:"); // "메모:"
            noteLabel.setBounds(20, 270, 80, 20);
            add(noteLabel);
            noteArea = new JTextArea();
            noteArea.setLineWrap(true);
            noteArea.setWrapStyleWord(true);
            JScrollPane sp = new JScrollPane(noteArea);
            sp.setBounds(20, 290, 340, 100);
            add(sp);

            JButton save = new JButton("\uC800\uC7A5"); // "저장"
            save.setBounds(140, 410, 100, 30);
            add(save);
            save.addActionListener(e -> saveRecord());

            setLocationRelativeTo(owner);
            applyRestaurantDefaults();
            setVisible(true);
        }

        void applyRestaurantDefaults(){
            Object selected = restCombo.getSelectedItem();
            if(selected == null) return;
            String restName = selected.toString().trim();
            if(restName.isEmpty()) return;

            RestaurantPreset preset = restaurantProfiles.get(restName);
            if(preset == null) return;

            for(int i = 0; i < categoryCombo.getItemCount(); i++){
                if(preset.category.equals(categoryCombo.getItemAt(i))){
                    categoryCombo.setSelectedIndex(i);
                    break;
                }
            }
            selectedX = preset.x;
            selectedY = preset.y;
            locationField.setText("\uC704\uCE58: (" + selectedX + ", " + selectedY + ")"); // "위치: ("
        }

        void selectLocationOnMap(){
            JDialog picker = new JDialog((Frame)SwingUtilities.getWindowAncestor(this), "\uC704\uCE58 \uC120\uD0DD", true); // "위치 선택"
            picker.setSize(1000, 800);
            picker.setResizable(false);
            picker.setLayout(null);

            ImageIcon mapIcon = new ImageIcon("images/\uC9C0\uB3C4.jpg"); // "images/지도.jpg"
            JLabel pickerMap = new JLabel(mapIcon);
            pickerMap.setBounds(0, 0, 1000, 800);
            picker.add(pickerMap);

            JLabel hint = new JLabel("\uC9C0\uB3C4\uB97C \uD074\uB9AD\uD574\uC11C \uC704\uCE58\uB97C \uC120\uD0DD\uD558\uC138\uC694"); // "지도를 클릭해서 위치를 선택하세요"
            hint.setOpaque(true);
            hint.setBackground(new Color(255, 255, 255, 220));
            hint.setBounds(20, 20, 220, 30);
            picker.add(hint);

            pickerMap.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectedX = e.getX();
                    selectedY = e.getY();
                    locationField.setText("\uC704\uCE58: (" + selectedX + ", " + selectedY + ")");
                    picker.dispose();
                    AddRecordDialog.this.toFront();
                    AddRecordDialog.this.requestFocus();
                }
            });

            picker.setLocationRelativeTo(AddRecordDialog.this);
            picker.setVisible(true);
        }

        void saveRecord(){
            Object restObj = restCombo.getSelectedItem();
            String restName = (restObj != null) ? restObj.toString().trim() : "";

            if(restName.isEmpty()){
                JOptionPane.showMessageDialog(this,"\uC2DD\uB2F9\uBA85\uC744 \uC785\uB825\uD558\uC138\uC694"); // "식당명을 입력하세요"
                return;
            }
            if(selectedX < 0 || selectedY < 0){
                JOptionPane.showMessageDialog(this,"\uC9C0\uB3C4\uC5D0\uC11C \uC704\uCE58\uB97C \uC120\uD0DD\uD558\uC138\uC694"); // "지도에서 위치를 선택하세요"
                return;
            }

            String menu = menuField.getText().trim();
            String price = priceField.getText().trim();
            String date = dateField.getText().trim();
            String note = noteArea.getText().trim();
            String category = (String)categoryCombo.getSelectedItem();

            // Save location info to file (가-힣 -> \uAC00-\uD7A3 정규식 처리 완벽 보장)
            String safeRest = restName.replaceAll("[^a-zA-Z0-9\uAC00-\uD7A3_\\- ]","_").replaceAll(" ","-");
            String filename = date+"_"+safeRest+".txt";
            File f = new File("Records/"+filename);

            try(BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), Charset.forName("MS949")))){
                bw.write("\uC2DD\uB2F9\uBA85: "+restName+"\n"); // "식당명: "
                bw.write("\uCE74\uD14C\uACE0\uB9AC: "+category+"\n"); // "카테고리: "
                bw.write("\uBA54\uB274: "+menu+"\n"); // "메뉴: "
                bw.write("\uAC00\uACA9: "+price+"\n"); // "가격: "
                bw.write("\uB0A0\uC9DC: "+date+"\n"); // "날짜: "
                bw.write("\uBA54\uBAA8: \n"+note+"\n"); // "메모: "
                bw.flush();
            }catch(Exception ex){
                JOptionPane.showMessageDialog(this,"\uC800\uC7A5 \uC2E4\uD328: "+ex.getMessage()); // "저장 실패: "
                return;
            }

            // Save/update location only in the restaurant registry (not in the record file)
            saveRestaurantRegistryEntry(restName, category, selectedX, selectedY);

            // Reload and refresh
            reloadRecords();
            seedRestaurantProfilesFromRecords();
            loadMarkersFromRecords();
            dispose();
        }
    }

    void updateMarkerVisibility(){
        // Update marker visibility based on category filters
        for(Marker m : markers){
            // Prefer registry category if present, otherwise use marker's stored kinds
            String category = m.kinds;
            RestaurantPreset preset = restaurantProfiles.get(m.name);
            if(preset != null && preset.category != null && !preset.category.isEmpty()){
                category = preset.category;
            }

            JButton filterBtn = kindFilterMap.get(category);
            boolean visible = (filterBtn != null && Boolean.TRUE.equals(filterBtn.getClientProperty("checked")));
            m.button.setVisible(visible);
        }
    }

    void loadMarkersFromRecords(){
        // Clear existing markers (remove any JButton markers)
        markers.clear();
        Component[] comps = mapPanel.getComponents();
        for(Component comp : comps){
            if(comp instanceof JButton){
                mapPanel.remove(comp);
            }
        }

        // Load markers from Records folder
        File dir = new File("Records");
        if(!dir.exists()) return;

        File[] files = dir.listFiles((d,name)->name.toLowerCase().endsWith(".txt"));
        if(files==null) return;

        for(File f: files){
            try(BufferedReader br = Files.newBufferedReader(f.toPath(), Charset.forName("MS949"))){
                String line;
                String tmpRestaurantName = "";
                String tmpKinds = "\uAE30\uD0C0"; // "기타"
                int tmpX = -1, tmpY = -1;

                while((line = br.readLine()) != null){
                    if(line.startsWith("\uC2DD\uB2F9\uBA85:")) tmpRestaurantName = line.substring(4).trim();
                    if(line.startsWith("\uCE74\uD14C\uACE0\uB9AC:")) tmpKinds = line.substring(5).trim();
                    if(line.startsWith("\uC704\uCE58X:")) tmpX = Integer.parseInt(line.substring(4).trim());
                    if(line.startsWith("\uC704\uCE58Y:")) tmpY = Integer.parseInt(line.substring(4).trim());
                }

                final String restaurantName = tmpRestaurantName;
                final String kinds = tmpKinds;
                int x = tmpX;
                int y = tmpY;

                // Prefer coordinates from registry (restaurantProfiles) if available
                RestaurantPreset preset = restaurantProfiles.get(restaurantName);
                String useKinds = kinds;
                if(preset != null){
                    x = preset.x;
                    y = preset.y;
                    useKinds = preset.category != null ? preset.category : kinds;
                }

                if(!restaurantName.isEmpty() && x >= 0 && y >= 0){
                    JButton mark = new JButton();
                    String imagePath = "images/location" + useKinds + ".jpg";
                    ImageIcon markerIcon = new ImageIcon(imagePath);

                    if(markerIcon.getIconWidth() > 0 && markerIcon.getIconHeight() > 0){
                        mark.setIcon(markerIcon);
                        int iw = markerIcon.getIconWidth();
                        int ih = markerIcon.getIconHeight();
                        int mx = Math.max(0, Math.min(1000 - iw, x - iw/2));
                        int my = Math.max(0, Math.min(800 - ih, y - ih/2));
                        mark.setBounds(mx, my, iw, ih);
                    } else {
                        mark.setText("●");
                        mark.setFont(new Font("Arial", Font.PLAIN, 20));
                        mark.setForeground(Color.RED);
                        mark.setOpaque(true);
                        mark.setBackground(new Color(255, 0, 0, 200));
                        int bx = Math.max(0, Math.min(980, x));
                        int by = Math.max(0, Math.min(780, y));
                        mark.setBounds(bx-5, by-5, 20, 20);
                    }

                    mark.setToolTipText(restaurantName + " ("+kinds+")");
                    mark.setBorderPainted(false);
                    mark.setContentAreaFilled(false);
                    mark.setFocusPainted(false);

                    mapPanel.add(mark);
                    try{ mapPanel.setComponentZOrder(mark, 0); }catch(Exception ignore){}
                    markers.add(new Marker(mark, kinds, restaurantName));
                    mark.addActionListener(e -> openRestaurantRecordList(restaurantName));
                }
            }catch(Exception ex){
                // Skip files that can't be parsed
            }
        }

        // Ensure markers follow current filter state and refresh UI
        updateMarkerVisibility();
        mapPanel.revalidate();
        mapPanel.repaint();
    }

    static class Record{
        String path;
        LocalDate date;
        String restaurantName;
        public Record(String path, LocalDate date, String restaurantName){
            this.path = path; this.date = date; this.restaurantName = restaurantName;
        }
    }

    static class Marker{
        JButton button;
        String kinds;
        String name;
        public Marker(JButton button, String kinds, String name){
            this.button = button;
            this.kinds = kinds;
            this.name = name;
        }
    }

    static class RestaurantPreset{
        String category;
        int x;
        int y;

        RestaurantPreset(String category, int x, int y){
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

}

// 실행 진입점
class TasteMapAppRunner {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main());
    }
}